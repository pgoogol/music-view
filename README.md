# music-view

Osobiste narzędzie DJ-a do zarządzania biblioteką muzyczną (marka: **Sabor Latino**,
silnik genre-agnostyczny). Wzbogaca utwory o metadane i cechy audio (BPM, danceability,
energia, tempo), warstwę opisową (o czym utwór, opis PL, styl/gatunek) oraz prywatne uwagi
DJ-a; pozwala przeszukiwać bibliotekę i planować sety/playlisty z eksportem na Spotify.

**Stack:** Spring Boot 3.x (Java 21, pakiet `com.pgoogol`) + Spring Batch + PostgreSQL +
React (Vite). Źródła danych: Spotify, MusicBrainz (MBID), AcousticBrainz (dump),
Deezer (BPM), dowolny model LLM (analiza AI — provider konfigurowalny, do wyboru).

## Dokumentacja

| Dokument | Zawartość |
|---|---|
| [docs/KONCEPT.md](docs/KONCEPT.md) | Oryginalny koncept projektu (specyfikacja źródłowa) |
| [docs/DECYZJE.md](docs/DECYZJE.md) | Rejestr decyzji projektowych (ADR-lite) — obowiązujące rozstrzygnięcia |
| [docs/PLAN.md](docs/PLAN.md) | Rozbicie pracy: etapy, kamienie milowe, zależności, ryzyka |

## Szybki start

```bash
docker compose up -d     # Postgres 16 (profil local łączy się z tą bazą)
./mvnw verify            # build + testy
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
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
[docs/M19_WALIDACJA.md](docs/M19_WALIDACJA.md). **Etap 1 zamknięty** — dalej
Etap 2 (Spotify OAuth + playlisty) wg [docs/PLAN.md](docs/PLAN.md).
