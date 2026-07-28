# CLAUDE.md — ruleset projektu music-view

Osobiste narzędzie DJ-a do zarządzania biblioteką muzyczną. Spring Boot 3.x
(Java 21, Maven) + Spring Batch + PostgreSQL 16 + React (Vite, od M1.8).

Dokumentacja nadrzędna: [docs/KONCEPT.md](docs/KONCEPT.md) (specyfikacja),
[docs/DECYZJE.md](docs/DECYZJE.md) (obowiązujące decyzje D1–D19 — nadpisują koncept),
[docs/PLAN.md](docs/PLAN.md) (etapy i kamienie milowe). Odwołania „Dx" w kodzie
i commitach wskazują decyzje z DECYZJE.md.

## Komendy

| Cel | Komenda |
|---|---|
| Baza lokalnie (Postgres 16) | `docker compose up -d` |
| Pełny build + testy | `./mvnw verify` |
| Same testy | `./mvnw test` |
| Uruchomienie z lokalną bazą | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| Zatrzymanie bazy | `docker compose down` (z `-v` czyści dane) |

CI (GitHub Actions) uruchamia `./mvnw verify` na każdy push na `master` i każdy PR.

## Konwencje Java/Spring

- **Java 21**, Spring Boot 3.x, build wyłącznie Mavenem (wrapper `./mvnw` w repo).
- Pakiet bazowy **`com.pgoogol`** (D1); moduły jako podpakiety:
  `catalog`, `library`, `playlist`, `ingestion`, `enrichment`, `api`, `common`.
  Kod domenowy trzymaj w module, do którego należy; kontrolery REST, DTO i mappery
  w `api`; elementy współdzielone (np. rate limiting) w `common`.
- Rozdział danych (D3): `track_catalog` = dane deterministyczne utworu,
  `library_entry` = dane prywatne DJ-a. Nie mieszać.
- Konfiguracja przez `application.yml` + profile (`local` dla docker-compose);
  provider/model LLM i wersja promptu wyłącznie w konfiguracji (D15), nigdy w kodzie.
- Wstrzykiwanie przez konstruktor, bez `@Autowired` na polach.
- Zmiany schematu bazy **wyłącznie przez migracje Flyway** (`V…__opis.sql`, od M1.1);
  nigdy `ddl-auto=update`.
- Testy: JUnit 5; testy repozytoriów/integracyjne na realnym Postgresie przez
  **Testcontainers** (D12); klienci zewnętrznych API testowani na nagranych
  odpowiedziach (WireMock). Nowa logika = nowe testy w tym samym kamieniu.
- Klienci zewnętrznych API izolowani w dedykowanych klasach (`SpotifyClient` itd.)
  z limiterem i retry+backoff z `common/ratelimit`; MusicBrainz twardo 1 req/s.

## Rulesety szczegółowe (D16)

Obowiązują dla całego nowego kodu — zaadaptowane do projektu (usunięte sekcje
Kafka/WebFlux/Spring Security-JWT; szczegóły w DECYZJE.md D16). Konflikty
rozstrzygają DECYZJE.md i PLAN.md.

- [docs/rules/codestyle.md](docs/rules/codestyle.md) — styl kodu, nazewnictwo, konwencje Java 21/Spring
- [docs/rules/testing.md](docs/rules/testing.md) — narzędzia testowe, nazewnictwo testów, given/when/then, fixtures
- [docs/rules/errorhandling.md](docs/rules/errorhandling.md) — hierarchia wyjątków, format błędów API, logowanie, retry
- [docs/rules/database.md](docs/rules/database.md) — Flyway, JPA/Hibernate, zapytania, paginacja, cache
- [docs/rules/security.md](docs/rules/security.md) — sekrety, walidacja wejścia, tokeny Spotify, actuator

## Sekrety (D14)

- **Żadnych sekretów w repo** — ani w kodzie, ani w `application*.yml`, ani w testach,
  ani w commitach. Sekrety tylko przez zmienne środowiskowe / lokalny plik `.env`
  (jest w `.gitignore`); w repo utrzymujemy wyłącznie `.env.example` z pustymi
  wartościami.
- Zmienne: `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `LLM_PROVIDER`,
  `LLM_API_KEY`, `MB_USER_AGENT` (User-Agent z kontaktem — to nie sekret,
  ale konfiguracja środowiskowa).
- Klucz, który pojawił się jawnie (czat, log, commit) → traktuj jako spalony,
  zgłoś potrzebę rotacji.
- Brak auth w samej aplikacji (narzędzie lokalne, D2/D14) — nie dodawać systemu kont.

## Zasady pracy

- Jedna sesja = jeden kamień milowy z [docs/PLAN.md](docs/PLAN.md); kamień kończy się
  działającym, testowalnym przyrostem (`./mvnw verify` zielone).
- Schemat danych po M1.1 jest zamrożony — każda zmiana wymaga jawnej decyzji
  i migracji Flyway (ryzyko „dryf schematu" w PLAN.md).
- Nowe decyzje projektowe dopisuj do DECYZJE.md (ADR-lite), nie tylko do kodu.
- Komunikaty commitów po polsku, w trybie rozkazującym (jak historia repo).
