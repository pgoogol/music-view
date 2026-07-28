# Rejestr decyzji projektowych (ADR-lite)

Status wszystkich decyzji: **przyjęte** (2026-07-03). Decyzje nadpisują [KONCEPT.md](KONCEPT.md)
tam, gdzie się różnią. Numeracja D1–D19; odwołania §x wskazują sekcje konceptu.

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
