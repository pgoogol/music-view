# Rejestr decyzji projektowych (ADR-lite)

Status wszystkich decyzji **D1–D35: przyjęte**. D25–D30 zostały rozstrzygnięte *przed*
implementacją Etapów 4–5 (2026-08-01), żeby kamienie dało się wziąć w dowolnej kolejności
bez projektowania od zera, i są zrealizowane w M4.1–M5.3.
Decyzje nadpisują [KONCEPT.md](KONCEPT.md) tam, gdzie się różnią. Numeracja D1–D35;
odwołania §x wskazują sekcje konceptu.

---

## D1. Pakiet bazowy: `com.pgoogol`

Zamiast `com.saborlatino` z konceptu (§10). Struktura modułów bez zmian:
`catalog`, `library`, `playlist`, `ingestion`, `enrichment`, `api`, `common`.

## D2. Multi-user wycofany

Aplikacja jest **narzędziem osobistym jednego DJ-a**. Znika cały Etap 3 konceptu:

- brak encji `User` i systemu kont/auth,
- brak współdzielenia katalogu między użytkownikami (katalog nadal deduplikuje koszt
  wzbogacania — ten sam utwór w wielu playlistach wzbogacany raz),
- brak read modelu OpenSearch, projektora i tabeli `outbox` (§15a) — Postgres z `pg_trgm`
  i `tsvector`+GIN w zupełności wystarcza dla ~2500+ utworów,
- brak dedykowanej kolejki zadań — Spring Batch wystarcza (§18.3).

**Konsekwencja odwracalna:** dzięki D3 ewentualny powrót do multi-user to dodanie `user_id`
do `library_entry` i warstwy auth, bez przebudowy katalogu.

## D3. Podział catalog/library zostaje

- **`track_catalog`** — dane deterministyczne o utworze (metadane, cechy audio, analiza AI);
  zależą tylko od utworu.
- **`library_entry`** — dane prywatne DJ-a: uwagi, custom tagi, rating, `dj_slot_override`.

Rozdział fakt/estymacja (§2.3) zostaje: pola `bpm_source`, `model_used`, `enrich_version`
pozwalają przeliczać estymacje bez ponownego odpytywania źródeł faktów.

## D4. OAuth Spotify właściciela zostaje

Jednorazowe połączenie konta właściciela (Authorization Code + PKCE) — potrzebne do:
importu wszystkich własnych playlist (tryb C) i eksportu playlist na Spotify.
To nie jest system kont — brak logowania do samej aplikacji (D14).

## D5. Uproszczony zestaw pól

Tylko pola, których DJ realnie używa:

| Grupa | Pola |
|---|---|
| Podstawowe metadane | title, artist, album, year, duration_ms, popularity, explicit, album_image_url, isrc |
| Cechy audio | **bpm** (+`bpm_source`), **danceability**, **energy**, **tempo_class** (szybka/wolna), **musical_key** |
| Warstwa opisowa | **lyrics_theme** (o czym utwór), **description_pl**, **style** (free-form), **genre_family** |
| Prywatne (library) | **dj_notes**, custom_tags[], rating, dj_slot_override |
| Audyt wzbogacania | confidence, enriched_at, model_used, enrich_version |

**Wycofane z konceptu:** `genre_tags[]`, kraj artysty, playcount, bio artysty,
`lyrics_excerpt`, `lastfm_summary`, `style_source`.

## D6. Odchudzone źródła danych + kaskada BPM

| Źródło | Rola | Auth / limit |
|---|---|---|
| **Spotify** | metadane, ISRC, okładki; import/eksport playlist | Client Credentials; OAuth PKCE (D4) |
| **MusicBrainz** | wyłącznie lookup ISRC → MBID (klucz do AcousticBrainz) | User-Agent; twardo 1 req/s + cache |
| **AcousticBrainz** | realne BPM, tonacja, danceability — import podzbioru dumpa (D7) | brak (dump offline) |
| **Deezer API** | BPM fallback (lookup po ISRC / artist+title) | brak, darmowe |
| **LLM (provider do wyboru — D15)** | style, genre_family, lyrics_theme, description_pl, energy, confidence; BPM tylko w ostateczności | klucz API providera (jedyny koszt) |

**Wycofane:** Last.fm, Discogs, Genius, iTunes Search, GetSongBPM oraz **własna analiza audio**
(TarsosDSP/librosa) — interfejs `AudioAnalyzer` zostaje w projekcie jako stub na przyszłość.

**Kaskada BPM:** `acousticbrainz → deezer → llm`, wynik audytowany w polu `bpm_source`.
*(Aktualizacja D24: na czele kaskady stoją metryki wgrane ręcznie —
`manual → acousticbrainz → deezer → llm`.)*
Sanity-check half-time (§16.1) stosowany do każdego źródła (genre_family=latin i BPM<100 →
rozważ podwojenie).

**Ryzyko:** pokrycie BPM w Deezer bywa niepełne (pole `bpm` = 0 dla części utworów), dump AB
zamrożony w 2022. **Mitygacja:** kaskada trzech źródeł + raport pokrycia per źródło w M1.9 —
jeśli pokrycie okaże się słabe, odblokowujemy implementację `AudioAnalyzer` (analiza previewu)
jako osobną decyzję.

## D7. AcousticBrainz: import podzbioru dumpa w Etapie 1

Jednorazowy ETL: z dumpa AB wyciągamy rekordy pasujące do MBID-ów biblioteki → lokalna tabela
`audio_features`. Potem wyłącznie lokalne join-y, zero rate-limitów.

## D8. `genre_family` — kontrolowany enum, `style` — free-form

`latin | rock | pop | disco | disco_polo | electronic | hip_hop | other` (§11.2).
LLM mapuje utwór na jedną wartość `genre_family`; `style` pozostaje wartością swobodną
(np. „timba", „salsa dura", „italo disco").

## D9. `dj_slot` liczony w aplikacji

Slot (rozgrzewka/środek/szczyt/zamknięcie/przerwa) wyliczany z `bpm + energy + genre_family`
w warstwie serwisowej, NIE zapisywany w katalogu (zależy od kontekstu wieczoru).
Ręczny override DJ-a w `library_entry.dj_slot_override`.

## D10. Zadania wzbogacania = Spring Batch

Restartowalność (checkpointy per chunk), throttling, retry+backoff. Bez dodatkowej kolejki.
Każde zlecenie = job z parametrami `scope` (single/selected/missing) + `fields` (grupy pól).

## D11. Grupy pól wzbogacania: METADATA / AUDIO / AI

Redukcja z czterech grup konceptu (§5.2). `TAGS` i `TEXT` znikają wraz ze źródłami:

- **METADATA** — Spotify (fakty, cache na zawsze),
- **AUDIO** — AcousticBrainz/Deezer: bpm, danceability, musical_key, tempo_class (fakty),
- **AI** — LLM: style, genre_family, lyrics_theme, description_pl, energy, confidence
  (estymacje, przeliczalne przy zmianie providera/modelu/promptu).

## D12. Stack techniczny startowy

Maven, Spring Boot 3.x, Java 21, Spring Batch, Spring Data JPA, **Flyway** (migracje),
**Testcontainers** (testy z realnym Postgresem), `docker-compose` z Postgres 16 lokalnie,
springdoc-openapi (Swagger UI). Front: **Vite + React** (TypeScript).
Hosting (opcjonalny, na końcu): Neon + Railway/Fly.io + Vercel.

## D13. Viewer Etapu 1 = minimalny React + Swagger UI

Równolegle: springdoc-openapi od pierwszego endpointu (interfejs techniczny) oraz minimalny
front React (tabela biblioteki, wyszukiwanie, filtry, szczegóły utworu, panel wzbogacania) —
fundament pod pełny front Etapu 2.

## D14. Bezpieczeństwo kluczy

Bez auth w aplikacji (narzędzie lokalne). Sekrety wyłącznie w zmiennych środowiskowych /
`.env` (poza repo; w repo tylko `.env.example`): Spotify Client ID+Secret, klucz API
wybranego providera LLM (D15).
MusicBrainz wymaga tylko User-Agent z kontaktem; Deezer i dump AB — bez sekretów.
Klucze, które pojawiły się wcześniej jawnie w czacie — **zrotować** przed ewentualnym
upublicznieniem repo (§21).

## D15. Provider LLM do analizy utworów — dowolny (nierozstrzygnięty celowo)

Koncept zakładał na sztywno Claude/Anthropic w warstwie wzbogacania. Decyzja: **nie wiążemy
się z jednym providerem** — wybór zapadnie później. Dotyczy wyłącznie analizy danych utworów
(grupa AI); workflow deweloperski (Claude Code, CLAUDE.md) bez zmian.

- **Warstwa AI w aplikacji:** interfejs **`LlmClient`** (własna abstrakcja; alternatywnie
  Spring AI jako gotowa warstwa multi-provider). Provider, model i wersja promptu wyłącznie
  w konfiguracji (`LLM_PROVIDER`, `LLM_API_KEY`, model). Pola audytu `model_used`
  i `enrich_version` (D3) już to wspierają — zmiana providera/modelu = przeliczenie samych
  estymacji, bez ponownego odpytywania źródeł faktów.
- **Kryteria wyboru modelu (gdy zapadnie):** tani model klasy „mini/haiku", strukturalne
  wyjście JSON, sensowna wiedza muzyczna; łatwa podmiana dzięki abstrakcji.
- **`bpm_source`:** wartość `llm` (neutralna, zamiast `claude`).

## D16. Rulesety kodowania w docs/rules/

Przyjęto zewnętrzne rulesety (code style, testing, error handling, database, security)
w wersji **zaadaptowanej do projektu** — pliki w [docs/rules/](rules/), linkowane
z CLAUDE.md. Usunięte jako nieadekwatne:

- Kafka i testy kontraktowe (Pact) — brak messagingu i mikroserwisów,
- WebFlux/reactive — aplikacja jest Spring MVC,
- Spring Security (SecurityFilterChain, method security), JWT/logowanie, hasła/BCrypt,
  nagłówki security — brak auth w aplikacji (D2/D14).

Dostosowane do projektu: nazewnictwo migracji Flyway sekwencyjne `V{n}__opis.sql`
(jak w PLAN.md, zamiast datowanego), retry/circuit breakery pod klientów źródeł
(Resilience4j jako fundament `common/ratelimit`; 429 → honoruj `Retry-After`),
actuator ograniczony do `health,info`, cache MB trwały w bazie (D6), paginacja
offsetowa (skala ~2500 utworów). Konflikty rozstrzygają DECYZJE.md i PLAN.md.

## D17. Doprecyzowania schematu danych (M1.1)

Rozstrzygnięcia przy zamrażaniu schematu — uzupełniają diagram ERD z PLAN.md:

- **`library_entry.spotify_id` UNIQUE** — jeden wpis biblioteki na utwór; dedup
  z M1.2 egzekwowany także na poziomie bazy.
- **`audio_features.spotify_id` UNIQUE** — relacja 0..1 do utworu (jak w ERD).
- **`playlist_track`**: UNIQUE `(playlist_id, spotify_id)`; `position` bez unikalności
  (swobodne reordery w M2.3); FK do playlisty z `ON DELETE CASCADE`.
- **`library_entry.source`** dostaje enum `LibrarySource` (FILE / PLAYLIST /
  FOREIGN_PLAYLIST) — uzupełnienie listy enumów z M1.1.
- Enumy zapisywane jako `varchar` z nazwami Javy (UPPER_SNAKE_CASE,
  `@Enumerated(STRING)`); bez CHECK-ów w bazie — rozszerzenie enuma nie wymaga migracji.
- Timestampy jako `timestamptz` (UTC), mapowane na `Instant`.
- `custom_tags` jako natywny `text[]`.
- Wyszukiwanie: kolumna generowana `search_vector` (tsvector, konfiguracja `simple`,
  title+artist+album) z indeksem GIN; indeksy GIN pg_trgm na `title` i `artist`;
  indeksy btree na `bpm`, `genre_family`, `isrc`.
- Zapytania „missing" per grupa pól (D11) po polach-wyznacznikach: METADATA →
  `isrc/year/duration_ms`, AUDIO → `bpm/musical_key/danceability/tempo_class`,
  AI → `style/genre_family/lyrics_theme/description_pl/energy`.

## D18. Trwały cache MusicBrainz w bazie (tabela techniczna poza ERD)

M1.3 wymaga cache'u wyników ISRC→MBID w bazie (D6: MB wyłącznie do MBID, twardy
1 req/s). Tabela `musicbrainz_isrc_cache(isrc PK, mbid NULL, resolved_at)`,
migracja V2; `mbid = NULL` oznacza potwierdzony brak wyniku (negative cache) —
ponowne wzbogacanie nie odpytuje MB drugi raz. To nie jest zmiana zamrożonego
modelu domenowego (ERD z PLAN.md) — tabela pomocnicza infrastruktury klienta.
Bez TTL: mapowanie ISRC→MBID traktujemy jak fakt deterministyczny (wyjątek
dopuszczony w docs/rules/database.md).

## D19. AudioAnalyzer — kryterium decyzji (M1.9)

Pipeline zwalidowano E2E na pełnej skali 2500 utworów (próba generalna na
danych syntetycznych — [RAPORT_POKRYCIA_M19.md](RAPORT_POKRYCIA_M19.md)):
kaskada D6 + fallback LLM domyka BPM do 100%, komplet pól D5 = 100%,
koszt LLM ≈ 0,0007 USD/utwór. Mechanizm działa; niepewna pozostaje wyłącznie
jakość pokrycia realnych źródeł (Deezer/dump AB) na prawdziwej bibliotece.

**Decyzja (kryterium):** `AudioAnalyzer` pozostaje stubem. Implementację
(analiza previewu) odblokowuje dopiero realny przebieg M1.9, jeśli:
BPM z faktów (AB+Deezer) < 70% biblioteki **lub** komplet pól D5 < 95%.
Wynik realnego przebiegu dopisać tutaj i do raportu (przebieg B).

## D20. Konto Spotify właściciela w bazie (tabela techniczna poza ERD)

M2.2 realizuje D4 (Authorization Code + PKCE). Rozstrzygnięcia:

- **Tabela `spotify_account`** (migracja V4) — jeden wiersz o stałym `id = 1`:
  narzędzie jest jednoosobowe (D2), więc ponowne połączenie nadpisuje ten sam
  rekord zamiast mnożyć konta. To nie jest zmiana zamrożonego modelu domenowego
  (ERD z PLAN.md), tylko infrastruktura klienta — jak cache MusicBrainz z D18.
- **Tokeny wyłącznie server-side:** `access_token`/`refresh_token` żyją w bazie,
  nie trafiają do odpowiedzi API (`/api/auth/spotify/status` zwraca sam stan)
  ani do logów. Odświeżanie jest leniwe — przy pierwszym użyciu po wygaśnięciu
  (margines 60 s).
- **Rozpoczęte logowanie (`state` + `code_verifier`) trzymamy w pamięci procesu,**
  nie w bazie: jest ważne 10 minut i dotyczy jednej sesji przeglądarki. Po
  restarcie aplikacji w trakcie logowania wystarczy powtórzyć `/login`.
- **Kod Spotify (klient, OAuth, konto) mieszka w `enrichment.spotify`** — bez
  nowego modułu najwyższego poziomu; lista modułów z CLAUDE.md zostaje bez zmian.
- **`library_entry.source` rozstrzygane po właścicielu playlisty:** playlista
  połączonego konta → `PLAYLIST` (tryb B/C), cudza → `FOREIGN_PLAYLIST` (tryb D).
  Bez połączonego konta każda importowana playlista jest obca.
- **Zakresy uprawnień:** `playlist-read-private`, `playlist-read-collaborative`
  (import trybu C) oraz `playlist-modify-private`, `playlist-modify-public`
  (eksport M2.4) — nadawane raz, przy łączeniu konta.

## D21. Sloty wieczoru i kontrakt kolejności setu (M2.3)

Doprecyzowanie D9 przy implementacji planowania setów:

- **`DjSlot` = enum `WARMUP | MIDDLE | PEAK | CLOSING | BREAK`** (rozgrzewka,
  środek, szczyt, zamknięcie, przerwa). Nie trafia do bazy jako kolumna katalogu —
  liczy go `DjSlotCalculator` przy odczycie playlisty (D9).
- **Kaskada wyliczania** (pierwszy pasujący warunek): bpm < 75 → `BREAK`;
  energia „low" albo bpm < 95 → `WARMUP`; energia „high" → `PEAK` przy bpm ≥ 120
  (dla gatunków parkietowych — latin/disco/disco_polo/electronic — już od 110),
  w przeciwnym razie `CLOSING`; reszta → `MIDDLE`. Brak bpm **i** energii = brak
  slotu; brak jednego z nich kaskadzie nie przeszkadza. Progi są punktem wyjścia —
  ostatnie słowo ma i tak override DJ-a.
- **`library_entry.dj_slot_override` przyjmuje wyłącznie nazwy `DjSlot`**
  (bez rozróżniania wielkości liter, zapis kanoniczny UPPER). To zawężenie
  kontraktu `PATCH /api/library/tracks/{id}` z M1.7, gdzie pole było swobodnym
  tekstem; pusty łańcuch nadal czyści wartość.
- **`PUT /api/playlists/{id}/tracks` wymaga permutacji** obecnego składu —
  pominięcie utworu w nowej kolejności to błąd (`PLAYLIST_ORDER_MISMATCH`),
  a nie ciche usunięcie go z setu. Usuwanie ma własny endpoint i przenumerowuje
  pozostałe pozycje, żeby zostały zwarte (0..n-1).

## D22. Architektura frontu po rozbudowie UI (M3.1)

Viewer z M1.8 był jedną przewijaną stroną z panelami; przy realnej bibliotece
(2500 utworów) i planowaniu setów przestało to wystarczać. Rozstrzygnięcia:

- **Cztery widoki zamiast jednej strony** — Biblioteka / Sety / Import /
  Wzbogacanie, przełączane zakładkami. Zaznaczenie utworów jest wspólne dla
  wszystkich widoków (pasek zaznaczenia), bo przepływ „znajdź w bibliotece →
  dorzuć do setu → wzbogać" przechodzi przez trzy z nich.
- **Stan widoku w hashu adresu, bez biblioteki routera** — `#/library?q=…&sort=BPM`.
  Filtry, sortowanie, strona, otwarty set i otwarty utwór przeżywają odświeżenie
  strony i dają się wkleić w zakładki przeglądarki. Aplikacja jest serwowana
  statycznie i ma cztery ekrany, więc router (i jego zależności) byłby kosztem
  bez zysku.
- **Bez frameworka CSS** — własne zmienne i klasy w `styles.css`. Jeden motyw
  (ciemny), jeden użytkownik; Tailwind/biblioteka komponentów dokładałaby build
  i słownictwo bez realnej korzyści.
- **Sortowanie liczy baza, nie przeglądarka.** Front sortował dotąd tylko
  widoczną stronę wyników, co przy paginacji wprowadzało w błąd. `GET /api/catalog/tracks`
  dostaje `sort` (enum `CatalogSort` — biała lista kolumn, żaden fragment SQL-a
  nie przychodzi z zewnątrz) i `direction`; braki (`NULL`) lądują zawsze na końcu,
  a energia sortuje się siłą (low → medium → high), nie alfabetycznie.
  Wartość spoza enuma to `400 INVALID_PARAMETER`, nie 500.
- **Kolory faz wieczoru to rampa porządkowa jednego odcienia** (rozgrzewka →
  zamknięcie), a nie paleta „kolor na fazę": fazy są uporządkowane, więc czyta się
  je jak skalę. Etykieta tekstowa towarzyszy każdemu kolorowi (kolor nigdy nie
  niesie znaczenia sam), a nazwy slotów zostają w kolorze tekstu — na kropce
  kolorystycznej, nie na literach, żeby utrzymać kontrast na ciemnym tle.
- **Ostrzeżenia o secie liczy front** (skok tempa > 15 BPM między sąsiadami,
  utwór bez BPM, cofnięcie fazy wieczoru) — to podpowiedzi do ręcznego układania,
  nie reguły domenowe; backend pozostaje przy wyliczaniu slotu (D9/D21).
  Auto-układanie („Ułóż wg faz wieczoru") wysyła zwykłą permutację przez istniejące
  `PUT /api/playlists/{id}/tracks`.
- **Testy frontu: Vitest + Testing Library (jsdom)**, uruchamiane w CI obok
  `mvn verify`. Logika bez UI (statystyki setu, ostrzeżenia, układanie, parsowanie
  adresu, formatery) siedzi w czystych modułach i jest testowana bez renderowania.

## D23. Motyw „konsola" (retro-futuryzm), filtry biblioteczne i przegląd playlist (M3.2)

UI z M3.1 był poprawny, ale bezosobowy; do tego wyszukiwarka nie umiała odpowiedzieć
na najczęstsze pytanie DJ-a („co z tego mam już u siebie?"), a zaimportowane playlisty
dawało się obejrzeć tylko przez planer setów. Rozstrzygnięcia:

- **Motyw retro-futurystyczny („konsola"), nadal jeden i ciemny.** Pulpit statku
  z lat 70.: bursztynowy CRT i cyjanowe podświetlenia na granatowej czerni,
  moduły ze ściętym narożnikiem (`border-radius` z parą wartości) i wewnętrznym
  włosem świetlnym, pigułkowe formanty, chromowany napis marki (gradient przycięty
  do liter), linie kineskopu jako nakładka `body::after` (`pointer-events: none`),
  krzywa tempa rysowana dwa razy — rozmyta poświata pod ostrym odczytem
  (`feGaussianBlur`). Cała dekoracja z gradientów i SVG inline, **żadnych zasobów
  z sieci ani webfontów** — narzędzie ma działać offline.
- **Podział ról kolorów:** bursztyn = akcja (to, co klikalne), cyjan = stan
  i pomiar (nagłówki, wartości, wykresy). Fazy wieczoru zostają porządkową rampą
  jednego odcienia z etykietą przy każdym kolorze (D22).
- **Wersaliki i font o stałej szerokości tylko w „przyrządach"** — nagłówkach,
  zakładkach, przyciskach, etykietach filtrów i liczbach (BPM, czasy, liczniki).
  Tytuły i wykonawcy w tabeli zostają w foncie systemowym i normalnej wielkości
  liter, bo biblioteka ma 2500 wierszy i to ona jest treścią, nie ozdobą.
  Font wyświetlaczowy ze stosu systemowego (Bahnschrift / DIN Alternate /
  Eurostile / Futura → `sans-serif`). Przesunięcia znikają przy
  `prefers-reduced-motion`; poświaty zostają, bo nie są ruchem.
- **Wyszukiwarka katalogu filtruje po bibliotece, ale nie zwraca jej danych.**
  `GET /api/catalog/tracks` dostaje `inLibrary`, `ratingMin` i `tag`; SQL dokłada
  `left join library_entry` (kolumna `spotify_id` jest UNIQUE, więc złączenie nie
  zwielokrotnia wierszy). Odpowiedzią nadal jest `TrackResponse` — dane prywatne
  DJ-a (D3) zostają w `/api/library/*` i w szufladzie utworu. Z tego samego powodu
  **nie ma sortowania po ocenie**: kolumny z oceną nie ma w tabeli, więc porządek
  byłby niewidoczny.
- **Słownik tagów jako osobny endpoint** (`GET /api/library/tags`, `unnest`
  po `custom_tags`) — filtr tagu podpowiada wartości zamiast wymagać pamięci.
- **Playlisty dostają własny widok do czytania, planer zostaje do pisania.**
  Zakładka Playlisty pokazuje wszystko, co jest w tabeli `playlist` (import
  ze Spotify i sety z planera): kafle z szukaniem po nazwie, a w środku szukanie
  po utworach, krzywa tempa i **zwijane sekcje faz wieczoru** — playlista na 200
  pozycji nie mieści się na ekranie inaczej. Zmiana kolejności, eksport i usuwanie
  zostają w zakładce Sety; z podglądu prowadzi tam jeden przycisk.
- **Import własnych playlist raportuje w modalu, nie w toaście.** Operacja trwa
  (playlista po playliście, limity Spotify), a raport per playlista jest tym,
  po co się ją uruchamia — toast z auto-znikaniem gubił wynik długiej operacji.
- **Import z pliku CSV zniknął z UI, endpoint został.** Biblioteka jedzie ze
  Spotify (tryby B/C/D z D6); `POST /api/ingest/file` zostaje jako awaryjne
  wejście trybu A i jest nadal pokryty testami — usunięcie go z ekranu to decyzja
  o UI, nie o API.

## D24. Metryki utworów wgrywane ręcznie z CSV (M3.3)

Spotify wyłączył `audio-features` i `audio-analysis` 27.11.2024 dla wszystkich aplikacji
bez wcześniejszego rozszerzenia limitu, więc grupa AUDIO (D11) stoi na dwóch niepełnych
źródłach: dump AcousticBrainz zamrożony w 2022 i Deezer z dziurami w polu `bpm`.
Rozważane obejście przez serwisy typu chosic.com odrzucone: nie mają API (zostałoby
skrobanie HTML za Cloudflare), a dane, które pokazują, to te same audio-features Spotify —
pobieranie ich tą drogą omija wyłączenie i łamie ToS obu stron. Do czasu innego źródła
(otwarte pozostaje odblokowanie `AudioAnalyzer` — D19) **metryki wgrywamy ręcznie plikiem CSV**.

- **Osobna tabela `manual_metrics`** (Flyway V5, klucz `spotify_id`, 0..1 rekord na utwór),
  a nie kolumny w `track_catalog` — plik jest surowym źródłem, katalog jego projekcją.
  Dzięki temu ponowny import odtwarza pola katalogu bez zgadywania, co skąd przyszło,
  i mieści się w regule „schemat po M1.1 zmienia się tylko migracją" bez ruszania D5.
- **Zakres pliku to wyłącznie cechy audio**: bpm, tonacja, Camelot, danceability, energy,
  valence, acousticness, instrumentalness, speechiness, liveness, głośność, metrum.
  Metadane (tytuł, album, popularność, explicit) nadal bierze Spotify, a gatunki i warstwa
  opisowa zostają przy LLM-ie — **wzbogacanie AI działa bez zmian**.
- **Dopasowanie po `spotify_id`, awaryjnie po ISRC** (wersaliki po obu stronach). ISRC
  identyfikuje nagranie, więc jeden wiersz może uzupełnić kilka jego wydań w katalogu.
  Utwór spoza katalogu **nie jest zakładany** — trafia do raportu jako pominięty;
  biblioteka jedzie ze Spotify (D6, tryby B/C/D), plik tylko dokłada metryki.
- **Skala 0..1 w bazie.** Eksporty podają cechy raz jako ułamek, raz w procentach —
  wartość powyżej 1 traktujemy jako procent. Tonacja normalizowana do zapisu
  AcousticBrainz („G minor", „C major"), bo `musical_key` ma znaczyć zawsze to samo.
- **`BpmSource.MANUAL` na czele kaskady** D6: `manual → acousticbrainz → deezer → llm`.
  Korekta half-time (§16.1) obowiązuje tak samo jak dla pozostałych źródeł, więc salsa
  z pliku (96) ląduje w katalogu jako realne 192 — po ustaleniu `genre_family` przez AI
  korekta jest powtarzana (idempotentna).
- **Zmierzona energia bije estymatę LLM-a.** `track_catalog.energy` zostaje tekstem
  (D11: `low/medium/high`), bo tak filtruje i sortuje front; wartość z pliku progujemy
  (poniżej 0,40 → low, poniżej 0,70 → medium, wyżej → high), a surowa liczba zostaje w `manual_metrics`
  do podglądu. Job wzbogacania nie nadpisuje energii z pliku wynikiem AI; reszta analizy
  (styl, `genre_family`, o czym utwór, opis) pozostaje w rękach LLM-a.
- **Gatunek z pliku wypełnia lukę, ale jej nie zajmuje.** Kolumny `Genres`/`Parent Genres`
  są mapowane na `genre_family` (D8) i zapisywane **tylko wtedy, gdy utwór jeszcze go nie ma** —
  bez rodziny gatunkowej nie policzymy slotu wieczoru (D9) ani korekty half-time, a na
  wzbogacenie AI można czekać długo. Właścicielem pola zostaje LLM: przy najbliższym
  wzbogacaniu nadpisze wartość z pliku. Gdy nic nie pasuje do enuma, pole zostaje puste.
- **Plik jest źródłem prawdy dla `manual_metrics`** — ponowny import nadpisuje rekord
  w całości (brak kolumny = kasowanie wartości), ale projekcja na katalog nadpisuje
  tylko pola niepuste, żeby BPM z innego źródła nie znikał bez powodu.
- **Wejście: `POST /api/ingest/metrics`** (multipart) + panel „Metryki utworów (CSV)"
  w zakładce Import; podgląd surowych wartości w szufladzie utworu przez
  `GET /api/catalog/tracks/{spotifyId}/metrics` (204, gdy utwór nie ma metryk).
  Format pliku i kolumny: [METRYKI_CSV.md](METRYKI_CSV.md).

**Tymczasowość jest świadoma:** to obejście, nie docelowe źródło. Gdy wróci sensowne API
albo zapadnie decyzja o `AudioAnalyzer` (D19), `manual_metrics` zostaje jako jedno ze źródeł
kaskady — zmienia się tylko to, kto je wypełnia.

---

## D25. Zgodność harmoniczna z tonacji, reszta metryk jako filtry (M4.1)

Camelot jest w bazie od V5 (`manual_metrics.camelot`), ale wyłącznie do oglądania
w szufladzie utworu; pozostałe zmierzone cechy (`valence`, `instrumentalness`,
`speechiness`, `liveness`, `loudness_db`, `time_signature`) nie są czytane przez nic
poza `TrackDetails`. Rozstrzygnięcia:

- **Camelot liczy aplikacja z `track_catalog.musical_key`, a nie czyta z pliku.**
  Tonacja jest w katalogu i wpada tam z dwóch źródeł (metryki ręczne D24, dump
  AcousticBrainz D7), podczas gdy `manual_metrics.camelot` istnieje tylko dla utworów
  z wgranego pliku — liczenie z `musical_key` daje pokrycie wszędzie tam, gdzie w ogóle
  znamy tonację. Mapowanie tonacja ↔ Camelot jest bijekcją na 24 wartościach, więc nic
  po drodze nie ginie. To ta sama zasada co przy `dj_slot` (D9): wartość wyprowadzalna
  z danych nie zostaje kolumną.
- **Parser tonacji przyjmuje obie notacje enharmoniczne** („D# minor" = „Eb minor").
  D24 normalizuje zapis do konwencji AcousticBrainz, ale plik od DJ-a bywa niesforny,
  a tonacja ma znaczyć zawsze to samo.
- **`manual_metrics.camelot` zostaje surową wartością z pliku** (D24: plik jest źródłem
  prawdy dla swojej tabeli) i służy do kontroli — rozjazd z wyliczeniem z `musical_key`
  trafia do logu przy imporcie, bo zwykle znaczy, że plik i katalog mówią o innym nagraniu.
- **Zgodność harmoniczna = ten sam klucz, ±1 na kole, względna dur/moll.** Cztery wartości,
  klasyczny zestaw miksowania harmonicznego. Skok energetyczny (+2 na kole) świadomie
  pomijamy — to chwyt na konkretny moment wieczoru, nie reguła, i wymaga ucha, nie filtra.
- **Filtr tłumaczy się na `musical_key in (…)`, nie na kolejne złączenie.** Zbiór zgodnych
  Camelotów (najwyżej cztery) aplikacja mapuje z powrotem na nazwy tonacji *przed*
  zapytaniem, więc wyszukiwarka zostaje przy jednym `left join` z D23, a plan zapytania
  się nie zmienia.
- **Koło kwintowe jest w backendzie, sama reguła zgodności we froncie.** Backend zwraca
  wyliczony `camelot` w DTO (pole liczone, jak `djSlot`); porównanie dwóch etykiet
  („ta sama liczba, ±1 modulo 12, ta sama litera") to kilka linii w TS i nie jest
  duplikacją mapowania. Zgodne z D22: ostrzeżenia o secie liczy front.
- **Pozostałe metryki wchodzą jako filtry, nie jako kolumny katalogu.** `valenceMin/Max`,
  `instrumentalMin` i `livenessMax` przez `left join manual_metrics` (klucz tabeli to PK,
  więc złączenie nie zwielokrotnia wierszy katalogu) — wzorzec z D23. Przenoszenie ich
  do `track_catalog` łamałoby D24: plik jest surowym źródłem, katalog jego projekcją,
  i projektujemy tylko to, czego używa reszta aplikacji.
- **`loudness_db` i `time_signature` nie są filtrami, tylko ostrzeżeniami w secie.**
  Skok głośności powyżej 3 dB między sąsiadami i metrum inne niż 4/4 to informacja
  o przejściu, a nie kryterium wyboru utworu — nikt nie szuka „utworów w 3/4",
  ale każdy chce wiedzieć, że taki właśnie stoi w kolejce.
- **Bez sortowania po metrykach** — dokładnie z powodu podanego w D23 przy ocenie:
  kolumny nie ma w tabeli biblioteki, więc porządek byłby dla DJ-a niewidoczny.
- **Filtry metryk działają na podzbiorze biblioteki** (tylko utwory z wgranym plikiem),
  więc UI musi to mówić wprost licznikiem „X z Y utworów ma metryki". Bez tego pusty
  wynik wygląda jak awaria, a jest brakiem danych.

## D26. Generator setu zwraca propozycję, nie zapisuje playlisty (M4.2)

M3.1 dało „Ułóż wg faz wieczoru" — permutację istniejącego składu. Kolejny krok to
zbudowanie setu z biblioteki na zadany czas. Rozstrzygnięcia:

- **Generator niczego nie zapisuje.** `POST /api/sets/propose` zwraca kolejność utworów
  i ostrzeżenia; playlistę zakłada DJ istniejącą drogą (`POST /api/playlists` +
  `POST /{id}/tracks`). Set ułożony maszynowo jest punktem wyjścia do ręcznej korekty,
  nie wynikiem — zapis w jednym kroku zamieniłby podgląd w sprzątanie po generatorze.
  Przy okazji generator zostaje bezstanowy i nie dubluje CRUD-a z M2.3.
- **Krzywa wieczoru jest stała i wpisana w kod:** WARMUP 25% / MIDDLE 30% / PEAK 30% /
  CLOSING 15% docelowego czasu. Parametryzacja krzywej to opcja dla jednego użytkownika,
  który i tak poprawia wynik ręcznie; progi zostają punktem wyjścia, jak w D21.
- **Ograniczenia dzielą się na twarde i miękkie.** Twarde zawężają pulę kandydatów:
  utwór raz w secie, ten sam wykonawca nie częściej niż raz na 30 minut. Miękkie są karami
  w ocenie kandydata: skok BPM powyżej 15, brak zgodności harmonicznej (D25), niska ocena,
  brak BPM. Gdyby miękkie zrobić twardymi, generator przy wąskiej bibliotece zwracałby
  pustkę zamiast setu z ostrzeżeniami — a DJ woli set do poprawienia niż komunikat.
- **Powtarzalność przez `seed` w żądaniu.** Czysto zachłanny generator daje za każdym
  razem ten sam set, więc po pierwszym uruchomieniu jest bezużyteczny. Wybór spośród
  pięciu najlepszych kandydatów z ziarnem: podany `seed` = wynik odtwarzalny (da się
  wrócić do propozycji sprzed korekty), brak `seed` = inna propozycja przy każdym kliknięciu.
- **Za uboga pula = krótszy set z powodem, nie błąd.** Odpowiedź niesie osiągnięty czas
  i informację, której fazy nie dało się domknąć — „mam za mało utworów na szczyt" jest
  użyteczną odpowiedzią, `400` nie jest.
- **Otwarte:** kryterium „dawno nie grany" wymaga historii grania, której w modelu nie ma.
  Gdyby powstała, wchodzi jako kolejny składnik oceny kandydata, bez zmiany kontraktu API.

## D27. Przegląd biblioteki — agregaty liczy baza (M4.3)

Pięć zakładek z M3.2 jest operacyjnych; nie ma ekranu odpowiadającego na pytanie
„co ja właściwie mam". Rozstrzygnięcia:

- **Jeden endpoint `GET /api/library/overview`, agregaty liczone w SQL** (`count(*) filter`,
  `width_bucket` na BPM). Front nie dostaje 2500 wierszy po to, żeby je zliczyć
  w przeglądarce — to jedyny sensowny podział pracy przy tej skali.
- **Bez cache.** Kilkanaście agregatów na ~2500 wierszach Postgres liczy w kilkanaście
  milisekund; docs/rules/database.md każe cache'ować tam, gdzie zapytanie boli, a tutaj
  nie boli. Cache dołożyłby za to pytanie o unieważnianie po każdym imporcie i po każdym
  jobie wzbogacania.
- **Udział `bpm_source` jest najważniejszą liczbą na ekranie, nie ozdobą.** Mówi, ile
  biblioteki stoi na faktach (manual / AcousticBrainz / Deezer), a ile na estymacie LLM —
  czyli podaje na bieżąco wskaźnik, który D19 uczynił kryterium decyzji o `AudioAnalyzer`,
  zamiast liczyć go raz na przebieg walidacyjny.
- **Wykresy rysowane inline w SVG**, jak `BpmCurve` z M3.1. D23 zabrania zasobów z sieci
  (narzędzie ma działać offline), a biblioteka wykresów dołożyłaby build i słownictwo
  przy pięciu wykresach.
- **`GET /api/enrich/missing-count` schodzi do jednego zapytania** z `count(*) filter (where …)`
  zamiast trzech osobnych `count`-ów; ten sam ekran i tak liczy więcej agregatów jednym
  przejściem po tabeli.

## D28. Przeliczanie estymat i bezpiecznik kosztowy (M5.1)

- **`EnrichmentScope.OUTDATED` domyka pętlę zaprojektowaną w D3/D15.** `model_used`
  i `enrich_version` zapisujemy od M1.1 właśnie po to, żeby po zmianie providera, modelu
  albo promptu przeliczyć **same estymaty** bez ponownego odpytywania źródeł faktów.
  Brakowało zakresu, który te pola czyta — nowy porównuje je z bieżącą konfiguracją
  (`llm.model` oraz `llm.prompt-version` zamieniona na liczbę tak jak dziś
  w `TrackEnricher.promptVersionNumber()`).
- **Zakres `OUTDATED` dotyczy wyłącznie grupy AI.** Fakty (METADATA/AUDIO) nie zależą
  od modelu ani od promptu, więc ich przeliczanie byłoby wywołaniem cudzego API bez powodu.
- **Koszt pokazujemy przed startem, nie po.** `GET /api/enrich/estimate` zwraca liczbę
  objętych utworów i widełki kosztu; stawki przenoszą się ze zmiennych środowiskowych
  `LlmSmokeTest` do konfiguracji (`llm.cost.input-per-1m`, `llm.cost.output-per-1m`),
  a zużycie tokenów bierze się z pomiaru M1.9 (~140 wejściowych + ~120 wyjściowych
  na utwór przy prompcie v1 i batchu po 5).
- **Twardy limit `llm.max-tracks-per-job`, domyślnie 500.** `SELECTED` ma limit 100 od M1.6,
  `MISSING` nie miał żadnego — a literówka w `LLM_MODEL` (drogi model zamiast klasy
  mini/haiku) przy 2500 utworach to rachunek, o którym dowiadujemy się po fakcie. Limit
  obowiązuje każdy zakres; jego podniesienie jest zmianą konfiguracji, czyli świadomą
  decyzją, a nie kliknięciem w UI.
- **Historia jobów jednym zapytaniem.** `JobExplorer` nie umie „ostatnie N wykonań dowolnej
  instancji", więc `EnrichmentService.listJobs` odpytuje wykonania osobno dla każdej
  instancji i przycina dopiero w pamięci. Tabele `BATCH_*` zakłada nasza migracja V3,
  więc zapytanie wprost do `BATCH_JOB_EXECUTION` (`order by job_execution_id desc limit n`)
  mieści się w tym, czym i tak zarządzamy — to nie jest sięganie do cudzych wnętrzności.

## D29. Blokada optymistyczna na danych DJ-a (M5.2)

- **Wersjonujemy `library_entry` i `playlist`, nie `track_catalog`.** Do katalogu pisze
  wyłącznie job wzbogacania (jeden pisarz), a konflikt optymistyczny kosztowałby tam
  restart całego chunka. Dane prywatne DJ-a i sety mają realnie dwóch pisarzy — dwie karty
  przeglądarki, laptop i telefon (scenariusz z DEPLOYMENT.md) — i są jedynym, czego nie
  odtworzy żadne API.
- **Wersja siedzi na agregacie, nie na elementach.** Zmiana składu albo kolejności setu
  podbija `playlist.version`, mimo że zmieniają się wiersze `playlist_track`. Wymaga to
  jawnego `OPTIMISTIC_FORCE_INCREMENT` na playliście, bo `@Version` na encji nadrzędnej
  nie reaguje na zapisy w podrzędnej — to pułapka implementacyjna, nie szczegół. Wersja
  per `playlist_track` byłaby bezużyteczna: reorder i tak dotyczy całej playlisty (D21
  wymaga permutacji całego składu).
- **Wersja jedzie w ciele odpowiedzi, nie w `ETag`/`If-Match`.** HTTP-owo poprawniejsze
  byłyby nagłówki, ale front trzyma cały obiekt w stanie widoku i wersja jedzie z nim
  za darmo, podczas gdy ETagi wymagałyby osobnego magazynu obok stanu. Narzędzie jest
  jednoosobowe — wybieramy prostszy kontrakt.
- **Konflikt to `409 RESOURCE_MODIFIED`**, obsłużony we froncie przeładowaniem rekordu
  **z zachowaniem tego, co DJ ma wpisane w polu**. Cichy zapis „ostatni wygrywa" jest
  gorszy od komunikatu, bo notatka ginie bez śladu i bez szansy na odtworzenie.

## D30. Dwie osobne aplikacje i testy E2E (M5.3)

Backend i front są **osobnymi aplikacjami**: własny obraz, własny cykl życia, własny
port. Front nie wchodzi do jara.

**Rozważone i odrzucone: jeden artefakt** (front pakowany do `target/classes/static`
przez profil `-Pfullstack` i serwowany przez Spring Boot). Kusiło prostotą uruchomienia,
ale sklejało dwie rzeczy, które zmieniają się w innym rytmie i inaczej się wdraża:
poprawka w CSS-ie wymagałaby przepakowania i restartu backendu, front przestałby dać się
wystawić na statycznym hostingu (Vercel z DEPLOYMENT.md), a build backendu zaczynałby
zależeć od Node'a w PATH. Prostota uruchomienia jest osiągalna taniej — `docker compose`
podnosi obie usługi jedną komendą.

- **Backend: `Dockerfile` w katalogu głównym** (maven → JRE), tylko aplikacja Spring Boot.
  `./mvnw package` nie wie nic o froncie i nie potrzebuje Node'a.
- **Front: `frontend/Dockerfile`** (node → nginx) — statyki z Vite podane przez nginx.
- **nginx przekazuje `/api` na backend, zamiast otwierać CORS.** Front woła adresy
  względne, więc zbudowany pakiet JS nie zawiera adresu API i ten sam obraz działa
  lokalnie i na serwerze; adres backendu siedzi w konfiguracji proxy (`API_HOST`/`API_PORT`,
  podstawiane przez entrypoint nginksa). CORS wymagałby wpuszczenia obcego originu do
  aplikacji, która nie ma auth (D2/D14) — to zły kierunek dla czegoś, co i tak trzeba
  postawić za bramką na hasło.
- **Bez fallbacku SPA.** Stan widoku siedzi w hashu (`#/library?q=…`, D22), więc przeglądarka
  nigdy nie prosi serwera o `/library` — wystarczy `index.html` pod `/`. Rezygnacja
  z routera z M3.1 opłaca się tutaj drugi raz.
- **Obie usługi w `docker-compose.yml` pod profilem `full`.** `docker compose up -d` musi
  nadal wstawiać samą bazę, bo tak wygląda praca nad kodem; pełny zestaw uruchamia
  `docker compose --profile full up -d --build` (front na :5173, API na :8080).
- **OAuth Spotify zostaje na loopbacku.** Spotify wymaga zgodności redirect URI znak w znak
  i nie przyjmie adresu w sieci lokalnej po HTTP, więc konto łączymy raz z laptopa
  (`http://127.0.0.1:8080/api/auth/spotify/callback`), a telefon korzysta z konta już
  połączonego — tokeny i tak żyją wyłącznie server-side (D20). Uwaga: redirect URI wskazuje
  **backend**, nie front — callback obsługuje API.
- **Podział na dwie aplikacje niczego nie zmienia w kwestii wystawienia na świat.** Nadal
  nie ma logowania (D2/D14), więc ostrzeżenie z DEPLOYMENT.md zostaje w mocy: publiczny
  adres wymaga najpierw bramki na hasło. „Dostęp z telefonu" znaczy tu sieć lokalna.
- **E2E: Playwright przeciw dwóm procesom** — zbudowany front podany statycznie
  (`vite preview`, odpowiednik nginksa z obrazu) i backend jako osobny proces, z API pod
  względnym `/api`. Testujemy ten układ, w którym aplikacja realnie działa, a nie serwer
  dev z HMR-em. Postgres z docker-compose, źródła zewnętrzne na stubie: test przepływu nie
  może zależeć od dostępności Spotify ani od klucza LLM, bo wtedy czerwone CI przestaje
  cokolwiek znaczyć. Osobny job w CI, żeby podstawowy build nie urósł.
- **Zakres E2E to jeden przepływ, a nie siatka przypadków**: import CSV → przegląd →
  biblioteka i utwór (z zapisem danych DJ-a, czyli wersjonowaniem z D29) → wzbogacenie AI
  na stubie → set → generator propozycji. Od testu E2E chcemy sygnału „całość się rozpięła";
  szczegóły należą do testów jednostkowych i integracyjnych, które są tańsze i celniejsze.
- **Eksport na Spotify świadomie zostaje poza E2E.** Wymagałby albo przeprowadzenia OAuth
  przez ekran zgody Spotify (którego nie kontrolujemy), albo wpisania tokenów wprost do bazy
  — czyli obejścia tego, co miałby sprawdzać. Ta ścieżka ma własny test integracyjny na
  WireMocku (`PlaylistExportIntegrationTest`); dublowanie jej w E2E kupiłoby ryzyko
  fałszywych alarmów bez nowego sygnału.
- **Stub źródeł zewnętrznych to kilkadziesiąt linii Node'a, nie WireMock.** WireMock obsługuje
  testy integracyjne backendu i zostaje tam, gdzie jest; stawianie drugiego procesu JVM obok
  aplikacji tylko po to, żeby oddać jedną odpowiedź LLM-a, byłoby kosztem bez zysku.

## D31. Import hurtem raportuje awarie, zamiast się przerywać

Dotyczy dwóch wejść, które przetwarzają wiele niezależnych porcji: importu własnych
playlist (tryb C) i importu metryk z plików CSV (D24).

- **Awaria jednej porcji nie przerywa przebiegu.** Import kilkudziesięciu playlist trwa
  kwadranse (jedna playlista = kilka wywołań Spotify z limiterem), więc wywrotka na
  dwudziestej kasowała efekt całej reszty i zmuszała do powtarzania od zera. Tak samo
  jeden felerny plik CSV nie może zabierać ze sobą tych, które weszły. Porcja, która
  padła, wraca w raporcie z `errorCode` i powodem — DJ wie, co powtórzyć.
- **Każda porcja ma własną transakcję.** Playlista idzie przez
  `PlaylistIngestionService`, plik przez `MetricsIngestionService` — wołane z osobnego
  beana, żeby proxy Springa realnie otwierało transakcję (samowywołanie by ją zgubiło).
  Rollback obejmuje wtedy wyłącznie porcję, która padła; ponowny import jest i tak
  idempotentny, więc powtórka nie dubluje danych.
- **Nieoczekiwany wyjątek nie wychodzi na zewnątrz treścią.** Do raportu trafia
  `INTERNAL_ERROR` i odesłanie do logów; komunikaty typowanych wyjątków (`AppException`)
  są już pisane dla użytkownika, więc te przepuszczamy w całości
  (docs/rules/errorhandling.md).
- **Kontrakty odpowiedzi zmieniają kształt na `{ imported, failed }`.**
  `POST /api/ingest/my-playlists` zwraca obiekt zamiast gołej listy, a
  `POST /api/ingest/metrics` — sumy partii plus sekcję `files[]` z raportem per plik.
  Numer wiersza bez nazwy pliku przestał cokolwiek znaczyć, gdy plików jest kilkanaście.
- **Wiele plików idzie w jednym żądaniu jako powtórzone pole `file`.** Eksport analizatora
  playlist powstaje per playlista, więc uzupełnienie biblioteki to kilkanaście plików pod
  rząd. Osobne żądanie na plik działałoby tak samo, ale raport rozjechałby się na
  kilkanaście toastów zamiast jednego podsumowania.

## D32. Domykanie gotowego setu: dobieranie i uzupełnianie (M4.4)

Generator z D26 układa wieczór **od zera**. W praktyce set częściej stoi już w połowie:
DJ ma trzon z zaznaczonych utworów i pyta „co po tym zagrać" albo „dociągnij mi to do
czterech godzin". Obie operacje działają na tym, co już w secie jest.

- **Nic nie zapisują — jak generator (D26).** `POST /api/sets/{id}/fill`
  i `POST /api/sets/{id}/suggest` zwracają podgląd; skład zmienia DJ istniejącą drogą
  (`POST /api/playlists/{id}/tracks`, `PUT /{id}/tracks`). Nowy endpoint zapisu byłby
  trzecią drogą do tej samej tabeli, a wersjonowanie agregatu (D29) trzeba by w nim
  odtworzyć od nowa.
- **Wstawienie w środek to dopisanie plus zmiana kolejności.** API dokłada utwór wyłącznie
  na koniec, więc front dopisuje i od razu wysyła nową kolejność z wersją **z odpowiedzi
  na dopisanie**, nie z widoku sprzed zmiany. Dwa żądania zamiast jednego są tu tańsze niż
  endpoint „wstaw na pozycję", który dublowałby kontrakt permutacji z D21.
- **Reguły oceny są jedne dla obu ścieżek** (`SetRules`). Gdyby dobieranie liczyło inaczej
  niż generator, DJ dostawałby dwie różne opinie o tej samej bibliotece — a to, co
  podpowiada „dobierz", musi być tym, co generator by wybrał.
- **Uzupełnianie liczy fazy nad całym zamówionym czasem**, nie nad tym, co zostało. Set na
  90 minut ciągnięty do czterech godzin ma dostać dalszy ciąg wieczoru (środek → szczyt →
  zamknięcie), a nie drugą rozgrzewkę. Utwory z setu zajmują początek osi: liczą się do
  upływu czasu i blokują powtórkę utworu oraz odstęp wykonawcy, ale nie wracają w wyniku.
  Set już dłuższy od zamówionego dostaje **notatkę, nie błąd** — to stan normalny.
- **Dobieranie nie losuje.** Generator losuje z piątki najlepszych, bo inaczej po pierwszym
  uruchomieniu byłby bezużyteczny (D26); tutaj DJ i tak dostaje listę i wybiera sam, więc
  ziarno nie miałoby czego powtarzać, a ta sama luka pytana dwa razy musi dać tę samą
  odpowiedź.
- **Kandydat ma dwóch sąsiadów.** Karę za przejście liczymy i od utworu przed luką, i do
  utworu za nią — inaczej dokładanie w środek psułoby przejście, które DJ przed chwilą
  ułożył. Odstęp wykonawcy sprawdzamy w obie strony od momentu wstawienia; utwór dołożony
  w środek i tak przesuwa resztę wieczoru, więc dokładniejsza arytmetyka nic by nie kupiła.
- **Fazę dobieramy od sąsiada, nie z krzywej.** Przy pojedynczej luce liczy się miejscowa
  ciągłość, a nie to, w którym procencie wieczoru ta luka wypada — inaczej dokładanie na
  koniec każdego, nawet krótkiego setu proponowałoby wyłącznie zamknięcia.
- **Na zewnątrz idą powody, nie punkty.** Odpowiedź niesie różnicę tempa wobec sąsiada
  i zgodność tonacji; sama punktacja jest względna i poza kolejnością listy nic nie znaczy.
  Nieznana tonacja albo brak BPM to `null` — brak danych, nie zderzenie (D25).
- **Bez rozszerzania testu E2E.** Przepływ z D30 to jeden przebieg, a nie siatka
  przypadków; domykanie ma testy jednostkowe reguł i integracyjne obu endpointów.
  Dołożenie kroku do E2E wymagałoby poszerzenia fikstury CSV o utwory spoza setu,
  czyli przestrojenia asercji całego przebiegu — koszt bez nowego sygnału.

## D33. Kształt wieczoru i tryby układania setu (M4.5)

Do M4.4 planer miał po jednym sposobie na każdą czynność: generator układał wieczór wg
krzywej 25/30/30/15 (D26), a gotowy set dawał się ułożyć wyłącznie wg faz D9. Wystarczało,
dopóki narzędzie układało „jakąś imprezę"; przy trzeciej pod rząd okazało się, że wesele
i klub to dwa różne przebiegi, a „ułóż" znaczy raz „prowadź przez fazy", a raz „chcę
płynne przejścia".

- **Profil zmienia proporcje faz, nie ich kolejność.** `SetCurve` to `STANDARD`
  (25/30/30/15 z D26), `WEDDING` (30/30/25/15 — goście jedzą, szczyt krótszy i wcześniej),
  `CLUB` (15/25/45/15 — parkiet gorący od początku) i `EVEN` (25/25/25/25 — urodziny,
  impreza firmowa, granie w tle). Fazy D9 zostają te same i w tej samej kolejności; to
  jedyne, co realnie różni te imprezy w danych, które mamy.
- **Wybór z listy, nie suwaki.** Cztery liczby do ustawienia to cztery pytania „ile", na
  które DJ przed imprezą nie zna odpowiedzi. Profil odpowiada na pytanie, które faktycznie
  sobie zadaje: „co to za impreza".
- **Nieznany profil to `400 INVALID_SET_CURVE`, nie ciche zejście do domyślnego.** Brak
  pola w żądaniu znaczy `STANDARD` (najczęstszy przypadek i zgodność wstecz z M4.2), ale
  literówka w nazwie musi być widoczna — inaczej DJ dostaje standardowy przebieg
  w przekonaniu, że układa klub.
- **Tryby układania liczy front** (`arrangeBy` w `setPlanner.ts`), tak jak ostrzeżenia
  i statystyki setu (D22). Backend dostaje gotową permutację składu przez
  `PUT /api/playlists/{id}/tracks` (D21) i nie musi wiedzieć, skąd się wzięła.
- **Cztery tryby, bo odpowiadają na cztery różne pytania:** `PHASES` (fazy D9, domyślny,
  bez zmian z M3.1), `TEMPO` (narastające BPM — utwory bez tempa na koniec, bo `null` to
  brak danych, nie zero), `HARMONY` (łańcuch po kole Camelot) i `ENERGY` (niska → wysoka,
  w grupie po tempie).
- **Układanie harmoniczne jest zachłanne i zostawia otwarcie DJ-a.** Pierwszy utwór
  zostaje na miejscu, każdy kolejny to najtańsze przejście z tego, co zostało; zderzenie
  tonacji przeważa nad każdym skokiem tempa, bo tego nie da się przemiksować. Optymalna
  trasa przez kilkadziesiąt utworów to problem komiwojażera — a wynik i tak idzie do
  ręcznej poprawki, więc dokładność kosztowałaby więcej, niż jest warta.
- **Nieznane BPM przy układaniu harmonicznym wyceniamy jak spory skok** (40), a nie jak
  zero. Nie wiemy, czy przejście zagra, więc nie stawiamy go przed przejściem, o którym
  wiemy.
- **Żaden tryb nie gubi i nie dokłada utworów** — kontrakt permutacji z D21 obowiązuje tak
  samo jak przy przeciąganiu; test sprawdza to dla każdego trybu osobno.

## D34. Plik z metrykami jako pierwsze źródło i falowe tryby układania (M4.6)

Dwie strony tej samej sprawy: dane wgrane świadomie z pliku (D24) mają wygrywać z tym,
co aplikacja zgadła, i mają realnie służyć układaniu setu, a nie tylko podglądowi.

- **Plik bije estymatę, nie tylko wypełnia lukę.** Do M4.5 rodzina gatunkowa z kolumn
  z gatunkami wchodziła wyłącznie wtedy, gdy utwór nie miał jeszcze gatunku, a najbliższe
  wzbogacanie AI i tak ją nadpisywało. Teraz jest odwrotnie: gatunek z pliku zostaje,
  a LLM uzupełnia tylko to, czego w pliku nie było. Kolumna analizatora playlist to tag
  z realnego opisu nagrania, a `genre_family` z LLM-a to zgadywanka z tytułu i wykonawcy.
  **Koszt tej zmiany:** felerna kolumna w eksporcie przestaje dawać się naprawić samym
  wzbogacaniem — trzeba poprawić plik i wgrać go ponownie. Świadomie: wgranie pliku jest
  decyzją DJ-a, a estymata nie.
- **Gatunek z pliku zapisujemy obok metryk** (migracja V7, `manual_metrics.genre_family`).
  Bez tego nie da się odróżnić gatunku z pliku od estymaty, a więc nie da się dać plikowi
  pierwszeństwa — to jedyna kolumna CSV, która do tej pory trafiała prosto na katalog
  i nigdzie nie zostawała. Reszta metryk była zapisywana w komplecie od M3.3, a BPM
  (`BpmSource.MANUAL` na czele kaskady D6) i zmierzona energia już wygrywały.
- **Do planera setu jadą wszystkie metryki, nie dwie wybrane.** `PlannedTrack`
  i `PlaylistTrackResponse` niosą cały rekord (`metrics`) zamiast płaskich `loudnessDb`
  i `timeSignature`. Powód jest konkretny: falowe układanie potrzebuje **zmierzonej
  energii jako liczby 0..1**, a `track.energy` z katalogu ma trzy wartości
  (`low/medium/high`, D11) i nie ułoży z nich fali. Kontrakt jest węższy do napisania
  i szerszy w treści niż dokładanie kolejnych płaskich pól przy każdym nowym trybie.
- **Intensywność ma kaskadę jak BPM (D6):** zmierzona energia z pliku → tempo
  przeskalowane z 60–200 BPM → zgrubna energia katalogu. Utwór bez żadnej z tych rzeczy
  ląduje w środku skali, bo wyrzucenie go z setu byłoby gorsze niż postawienie
  w przypadkowym miejscu.
- **Dwa nowe kształty, których nie da się dostać sortowaniem.** `TEMPO` i `ENERGY` rosną
  monotonicznie przez cały wieczór, a parkiet potrzebuje oddechu między szczytami:
  - **`WAVE`** — kilka narastań przedzielonych zejściem, każde następne wyżej.
    Posortowane po intensywności utwory rozdajemy do fal na przemian (jak karty), więc
    każda fala przechodzi cały zakres od dołu do góry, a kolejna startuje o oczko wyżej.
    Liczba fal wynika z długości setu (~5 utworów na falę, 2–4 fale) — parametr, którego
    DJ nie musi ustawiać, bo i tak poprawia wynik ręcznie.
  - **`ARC`** — jedno narastanie do szczytu w połowie i zejście: co drugi utwór
    z posortowanej listy idzie na zbocze wznoszące, reszta na opadające (odwrócona).
- **Układanie zostaje po stronie frontu** (D22/D33) i każdy tryb zwraca **permutację**
  składu (D21) — także nowe. Test sprawdza to trybowi po trybie, bo cicha utrata utworu
  przy układaniu jest gorsza od złej kolejności: kolejność widać, brak utworu nie.

## D35. Automatyczne odświeżanie playlist ze Spotify (M4.7)

Playlisty zmieniają się poza aplikacją — DJ dorzuca utwór w telefonie, a music-view
pokazuje stan sprzed ostatniego kliknięcia „Importuj moje playlisty". Odświeżanie idzie
więc samo: przy starcie aplikacji i potem co `ingestion.playlist-refresh.interval`
(domyślnie 5 minut).

- **To ten sam import co tryb C** (M2.2), tylko bez klikania — nie duplikujemy logiki
  i nie zmieniamy jej zachowania. Awaria pojedynczej playlisty nadal nie przerywa
  przebiegu (D31), więc odświeżanie w tle dziedziczy tę odporność za darmo.
- **`fixedDelay`, nie `fixedRate`** — odstęp liczy się od *zakończenia* poprzedniego
  przebiegu. To nie jest szczegół: import kilkudziesięciu playlist bywa dłuższy niż
  pięć minut, a przy `fixedRate` przebiegi wchodziłyby sobie na głowę i mnożyły
  wywołania Spotify. **Uwaga na koszt:** przy dużej bibliotece pięciominutowy odstęp
  znaczy w praktyce „odświeżaj bez przerwy" — dla takiej biblioteki interwał należy
  wydłużyć. Domyślne 5 minut zostaje, bo jest tym, o co poproszono, a wyłącznik
  i interwał są w konfiguracji.
- **Brak połączonego konta to normalny stan, nie awaria** (D20). Świeża instalacja nie
  ma przeprowadzonego OAuth; zadanie sprawdza to na wejściu i wychodzi po cichu, zamiast
  co pięć minut zasypywać logi błędami. Ten sam warunek chroni test E2E, który stawia
  aplikację bez konta.
- **Wyjątek nie wychodzi z zadania.** Przebieg leci w tle bez nikogo, kto by go obejrzał,
  a Spotify potrafi nie odpowiedzieć z powodów, które miną same. Powód ląduje w statusie,
  żeby UI mógł powiedzieć, że dane są nieświeże, i nie zatrzymuje harmonogramu.
- **Stan trzymamy w pamięci, nie w bazie.** To informacja o bieżącym uruchomieniu
  aplikacji — po restarcie i tak zaraz leci pierwsze odświeżenie, więc tabela niosłaby
  wyłącznie koszt migracji.
- **Zadanie rejestruje się także przy `enabled: false`** i sprawdza flagę na wejściu.
  Warunkowy bean byłby czystszy w teorii, ale kontroler musiałby go szukać przez
  `ObjectProvider`, żeby oddać status „wyłączone" — a to właśnie ten status jest
  potrzebny użytkownikowi, który się zastanawia, czemu nic się nie odświeża.
- **Front pyta o status, a nie o playlisty.** `GET /api/ingest/my-playlists/refresh-status`
  to odczyt z pamięci; widok odpytuje go raz na minutę i przeładowuje listę dopiero, gdy
  **zmieni się znacznik** ostatniego przebiegu. Pierwsza odpowiedź tylko zapamiętuje stan
  — inaczej samo wejście na zakładkę pobierałoby listę dwa razy. Bez tego odświeżanie
  w tle byłoby niewidoczne w otwartej karcie do czasu ręcznego przeładowania.
- **Testy integracyjne z połączonym kontem wyłączają zadanie** jawnie
  (`ingestion.playlist-refresh.enabled=false`). Zadanie ruszające w środku testu poszłoby
  po realne dane do Spotify — a test, który zależy od cudzego serwera, przestaje coś
  znaczyć (ten sam argument co w D30).
