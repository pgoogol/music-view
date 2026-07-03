# Rejestr decyzji projektowych (ADR-lite)

Status wszystkich decyzji: **przyjęte** (2026-07-03). Decyzje nadpisują [KONCEPT.md](KONCEPT.md)
tam, gdzie się różnią. Numeracja D1–D17; odwołania §x wskazują sekcje konceptu.

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
