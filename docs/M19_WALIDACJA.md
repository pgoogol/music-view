# M1.9 — runbook walidacji na realnej bibliotece

Wymagania: `.env` z realnymi `SPOTIFY_CLIENT_ID/SECRET`, `LLM_PROVIDER`,
`LLM_API_KEY`, `LLM_MODEL`, `MB_USER_AGENT` (z kontaktem); eksport CSV
biblioteki (Exportify).

## 1. Start

```bash
docker compose up -d
set -a && source .env && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# osobny terminal (opcjonalnie front):
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## 2. Import CSV (~2500 utworów)

Przez UI (panel „Import CSV") albo:

```bash
curl -X POST -F "file=@moja-biblioteka.csv;type=text/csv" http://localhost:8080/api/ingest/file
```

## 3. Wzbogacenie METADATA (Spotify → ISRC) — potrzebne przed ETL AB

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"scope":"MISSING","fields":["METADATA"]}' http://localhost:8080/api/enrich
watch -n5 curl -s http://localhost:8080/api/enrich/jobs?limit=1
```

## 4. Lookupy MB + ETL dumpa AcousticBrainz

Faza AUDIO joba buduje cache ISRC→MBID (twardo 1 req/s — dla 2500 utworów
~40+ min, job restartowalny). Następnie ETL dumpa: [AB_ETL.md](AB_ETL.md).
Kolejność praktyczna: najpierw jedno pełne wzbogacenie AUDIO (zbuduje cache
i pokryje BPM z Deezera), potem ETL, potem ponowne `{"fields":["AUDIO"]}` —
uzupełni bpm/tonację/taneczność z AB.

## 5. Pełne wzbogacenie AI (+ ewentualne braki)

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"scope":"MISSING","fields":["METADATA","AUDIO","AI"]}' http://localhost:8080/api/enrich
```

Job w razie awarii: `POST /api/enrich/jobs/{id}/restart` (dokańcza od checkpointu).

## 6. Raport pokrycia + koszt

```bash
docker exec -i music-view-postgres psql -U musicview -d musicview < scripts/coverage_report.sql
```

Tokeny/koszt: log `TrackAnalysisService` („Analiza AI zakończona: … tokeny=…")
lub pomiar próbki: `MV_SMOKE=true LLM_API_KEY=… ./mvnw test -Dtest=LlmSmokeTest`
(stawki przez `LLM_COST_INPUT_PER_1M`/`LLM_COST_OUTPUT_PER_1M`).

## 7. Domknięcie

Wyniki wpisz do [RAPORT_POKRYCIA_M19.md](RAPORT_POKRYCIA_M19.md) (przebieg B),
koszt do PLAN.md (ryzyka), rozstrzygnięcie AudioAnalyzera do DECYZJE.md (D19).
