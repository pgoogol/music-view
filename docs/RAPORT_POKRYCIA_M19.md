# Raport pokrycia M1.9 — walidacja E2E

## Przebieg A: próba generalna na pełnej skali (wykonana, dane syntetyczne)

Środowisko deweloperskie nie ma dostępu do realnych API muzycznych ani danych
właściciela, więc pipeline zwalidowano **na pełnej skali docelowej** przez
kompletny, realny stos (Postgres z docker-compose + aplikacja + job Spring
Batch), z zewnętrznymi API zastąpionymi deterministycznym stubem HTTP
(pokrycia źródeł dobrane do oczekiwań z DECYZJE.md D6).

**Parametry:** 2500 utworów z CSV (import 4,6 s — DoD M1.2 „< 1 min" ✓);
`audio_features` zasilone dla 734 utworów (symulacja po ETL dumpa AB — D7);
cache MB wypełniony (stan po wcześniejszych lookupach); Deezer zna BPM ~65%
ISRC; limity zapytań podniesione do 50 rps (stub lokalny — czasy sieci
nieporównywalne z produkcją).

**Wyniki (scope=MISSING, fields=METADATA,AUDIO,AI, chunk=5):**

| Miara | Wynik |
|---|---|
| Czas joba (2500 utworów) | 135 s, w tym 500 batchowanych wywołań LLM |
| Metadane (isrc/rok/czas/okładka) | 100% |
| BPM | **100%** — Deezer 46,3% / AcousticBrainz 29,4% / LLM 24,3% |
| tempo_class | 100% (wyliczane z BPM po korekcie half-time) |
| musical_key / danceability | 29,4% (tylko z AB — Deezer/LLM ich nie dają) |
| Pola AI (styl/gatunek/temat/opis/energia) | 100% |
| **Komplet pól D5** | **100% (DoD ≥ 95% ✓)** |
| Tokeny LLM | 350 000 wej. / 300 000 wyj. → **140 wej. + 120 wyj. / utwór** |

**Koszt LLM (przeliczenie po stawkach klasy mini/haiku ~1 USD/M wej.,
~5 USD/M wyj.):** ≈ 0,00074 USD/utwór → **~1,85 USD za całą bibliotekę 2500
utworów** — zgodne z szacunkiem z PLAN.md (sekcja ryzyk).

Dodatkowo job przeszedł pełny cykl restartowalności na tej skali: pierwsze
wykonanie padło na awarii transportowej przy pierwszym chunku (0 zapisów),
restart dokończył całość od checkpointu bez duplikatów.

## Przebieg B: realna biblioteka (do wykonania przez właściciela)

Procedura: [M19_WALIDACJA.md](M19_WALIDACJA.md). Po przebiegu wklej tu wyniki
`scripts/coverage_report.sql` oraz koszt z logów/`LlmSmokeTest` i rozstrzygnij
D19 (AudioAnalyzer).

| Miara | Wynik |
|---|---|
| Czas jobów | _do uzupełnienia_ |
| BPM per źródło (AB / Deezer / LLM / brak) | _do uzupełnienia_ |
| Komplet pól D5 | _do uzupełnienia (próg 95%)_ |
| Koszt LLM / utwór i łącznie | _do uzupełnienia_ |

**Interpretacja pod D19:** jeśli BPM z faktów (AB+Deezer) < 70% lub komplet
D5 < 95% → odblokować implementację `AudioAnalyzer` (analiza previewu);
w przeciwnym razie stub zostaje stubem.
