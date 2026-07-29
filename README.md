# music-view

Osobiste narzędzie DJ-a do zarządzania biblioteką muzyczną (marka: **Sabor Latino**,
silnik genre-agnostyczny). Wzbogaca utwory o metadane i cechy audio (BPM, danceability,
energia, tempo), warstwę opisową (o czym utwór, opis PL, styl/gatunek) oraz prywatne uwagi
DJ-a; pozwala przeszukiwać bibliotekę i planować sety/playlisty z eksportem na Spotify.

**Stack:** Spring Boot 3.x (Java 21, pakiet `com.pgoogol`) + Spring Batch + PostgreSQL +
React (Vite). Źródła danych: Spotify, MusicBrainz (MBID), AcousticBrainz (dump),
Deezer (BPM), plik CSV z metrykami wgrywany ręcznie (D24), dowolny model LLM
(analiza AI — provider konfigurowalny, do wyboru).

## Dokumentacja

| Dokument | Zawartość |
|---|---|
| [docs/KONCEPT.md](docs/KONCEPT.md) | Oryginalny koncept projektu (specyfikacja źródłowa) |
| [docs/DECYZJE.md](docs/DECYZJE.md) | Rejestr decyzji projektowych (ADR-lite) — obowiązujące rozstrzygnięcia |
| [docs/PLAN.md](docs/PLAN.md) | Rozbicie pracy: etapy, kamienie milowe, zależności, ryzyka |

## Szybki start

```bash
docker compose up -d     # Postgres 16 (profil local łączy się z tą bazą)
./mvnw verify            # build + testy backendu
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

cd frontend
npm install
npm run dev              # http://localhost:5173, proxy /api na :8080
npm test                 # testy frontu (Vitest + Testing Library)
```

Sekrety: skopiuj `.env.example` do `.env` i uzupełnij (plik `.env` nie trafia do repo — D14).

## Stan projektu

**Zakończone: Etap 0, M1.1 (schemat — zamrożony, D17), M1.2 (import CSV),
M1.3 (klienci źródeł), M1.4 (AudioFeatures + BpmResolver)** — kaskada BPM
D6: `audio_features` (AcousticBrainz) → Deezer → brak (dla AI), z korektą
half-time i audytem w `bpm_source`; ETL dumpa AB:
[docs/AB_ETL.md](docs/AB_ETL.md) + `scripts/filter_acousticbrainz_dump.py`
(dump poza repo); stub `AudioAnalyzer` (NOOP). **M1.5 (warstwa AI)**: `LlmClient`
niezależny od providera (openai-compatible / anthropic — D15), prompt „ekspert
muzyczny i DJ" wersjonowany w konfiguracji, batch po 5, walidacja JSON;
smoke + pomiar kosztu: `MV_SMOKE=true LLM_API_KEY=… ./mvnw test -Dtest=LlmSmokeTest`.
Smoke-test klientów źródeł: `MV_SMOKE=true ./mvnw test -Dtest=RealApiSmokeTest`.
**M1.6 (job wzbogacania)**: restartowalny pipeline Spring Batch — reader wg
`scope` (SINGLE/SELECTED/MISSING), chunk=5 z wzbogacaniem METADATA → AUDIO → AI
i zapisem inkrementalnym; checkpointy w tabelach BATCH_* (Flyway V3), restart
dokańcza bez duplikatów; `EnrichmentService` (start/status/restart/missing-count).
**M1.7 (REST + Swagger)**: pełne API Etapu 1 — Catalog (wyszukiwarka
tsvector+pg_trgm, filtry genre/bpm/tempo/energy, paginacja), Library
(lista z joinem, GET/POST/PATCH/DELETE danych prywatnych), Enrich (zlecenie,
joby, status, restart, missing-count); Swagger UI: `/swagger-ui.html`.
**M1.8 (viewer React)**: `frontend/` (Vite + React + TS, proxy dev na API) —
tabela biblioteki (wyszukiwarka, filtry, sortowanie, paginacja, zaznaczanie),
szczegóły utworu z edycją uwag/tagów/ratingu DJ-a, panel importu CSV
i panel wzbogacania z podglądem postępu joba; start:
`cd frontend && npm install && npm run dev` (backend na :8080).
**M1.9 (walidacja E2E)**: pipeline zwalidowany na pełnej skali 2500 utworów
(próba generalna — [docs/RAPORT_POKRYCIA_M19.md](docs/RAPORT_POKRYCIA_M19.md)):
komplet pól D5 = 100%, BPM domknięty kaskadą D6 + LLM, koszt ≈ $1.85/biblioteka;
raport pokrycia: `scripts/coverage_report.sql`, przebieg na realnej bibliotece:
[docs/M19_WALIDACJA.md](docs/M19_WALIDACJA.md). **Etap 1 zamknięty.**

**Etap 2 zamknięty** — integracja ze Spotify i planowanie setów.
**M2.1 (import playlist)**: `POST /api/ingest/playlist` przyjmuje link, URI
`spotify:playlist:…` albo samo id; utwory idą do katalogu z kompletem metadanych
i do biblioteki (dedup po `spotify_id`), playlista odtwarzana lokalnie razem
z kolejnością — ponowny import ją aktualizuje, nie duplikuje. **M2.2 (konto
Spotify)**: OAuth Authorization Code + PKCE (D4/D20) — `GET /api/auth/spotify/login`
→ ekran zgody → `/callback`; tokeny wyłącznie w bazie (nigdy w odpowiedziach API
ani w logach), odświeżane leniwie; `POST /api/ingest/my-playlists` wciąga wszystkie
własne playlisty (tryb C). **M2.3 (sety)**: CRUD `/api/playlists*`, skład
i kolejność utworów (drag&drop we froncie), slot wieczoru liczony z bpm + energy +
genre_family (D9/D21) z override'em DJ-a. **M2.4 (eksport)**:
`POST /api/playlists/{id}/export-to-spotify` — pierwszy eksport zakłada prywatną
playlistę na koncie, kolejne nadpisują jej zawartość (batch po 100 URI).
Przebieg na realnym koncie: [docs/M2_RUNBOOK.md](docs/M2_RUNBOOK.md);
wdrożenie (opcjonalne M2.5): [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

**M3.1 (rozbudowa UI)** — front rozbity na cztery widoki (Biblioteka / Sety /
Import / Wzbogacanie) ze stanem zapisanym w adresie (`#/library?q=…&sort=BPM`),
więc odświeżenie strony wraca do tych samych filtrów. Biblioteka: okładki, czas
utworu, znacznik „do wzbogacenia", sortowanie liczone przez bazę (`sort`
+ `direction` w `GET /api/catalog/tracks`, biała lista kolumn — D22). Sety:
statystyki (czas, zakres i średnia BPM), rozkład faz wieczoru, krzywa tempa,
ostrzeżenia o skokach BPM i cofnięciu fazy, układanie wg faz D9 jednym
kliknięciem, kolejność strzałkami albo przeciąganiem. Wzbogacanie: pokrycie pól
D11 i historia jobów z restartem. Testy frontu: `cd frontend && npm test`
(Vitest + Testing Library, uruchamiane też w CI).

**M3.2 (motyw „konsola" i przegląd playlist)** — UI w stylu retro-futurystycznym
(bursztynowy CRT i cyjan, moduły ze ściętym narożnikiem, chromowany napis marki,
linie kineskopu, krzywa tempa z poświatą; tytuły w tabeli zostają w foncie
systemowym — D23).
Wyszukiwarka katalogu filtruje też po bibliotece DJ-a: `inLibrary`,
`ratingMin`, `tag` w `GET /api/catalog/tracks` (+ słownik tagów
`GET /api/library/tags`), a filtry zapisują się w adresie. Nowa zakładka
**Playlisty**: kafle wszystkich playlist z szukaniem po nazwie, a w środku
szukanie po utworach, krzywa tempa i zwijane sekcje faz wieczoru. Import
własnych playlist kończy się modalem z raportem per playlista; import z pliku
CSV zniknął z UI (endpoint `POST /api/ingest/file` został w API).

**M3.3 (metryki z pliku CSV)** — Spotify wyłączył `audio-features` (27.11.2024),
więc cechy audio można tymczasowo wgrać ręcznie: `POST /api/ingest/metrics`
(multipart CSV) albo panel „Metryki utworów (CSV)" w zakładce Import. Wiersze
dopasowywane po `Spotify Track Id`, awaryjnie po ISRC; utwory spoza katalogu
trafiają do raportu, nie do biblioteki. Wartości lądują surowo w `manual_metrics`
i są rzutowane na katalog: BPM (z korektą half-time, `bpm_source=MANUAL`), tonacja,
Camelot, danceability i energia — zmierzona energia ma pierwszeństwo przed estymatą
LLM-a, reszta wzbogacania AI działa bez zmian. Format pliku i lista kolumn:
[docs/METRYKI_CSV.md](docs/METRYKI_CSV.md).
