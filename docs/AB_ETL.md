# ETL dumpa AcousticBrainz → `audio_features` (M1.4, D7)

Jednorazowy, ręczny krok. Dump AB (zamrożony w 2022) **nie trafia do repo** —
filtrowanie jest strumieniowe, nie wymaga rozpakowania całości na dysk.

Wymagania: Python 3.9+, `zstd` w PATH (dla `.tar.zst`), uruchomiona baza
(`docker compose up -d`), biblioteka po imporcie CSV (M1.2) i po wzbogaceniu
METADATA + lookupach MB (ISRC→MBID w `musicbrainz_isrc_cache`).

## 1. Pobierz dump lowlevel

Ze strony <https://acousticbrainz.org/download> — pliki
`acousticbrainz-lowlevel-json-20220623-*.tar.zst`.

## 2. Wyeksportuj mapowanie MBID → spotify_id

```bash
docker exec music-view-postgres psql -U musicview -d musicview -c \
  "\copy (select c.mbid, t.spotify_id
          from musicbrainz_isrc_cache c
          join track_catalog t on t.isrc = c.isrc
          where c.mbid is not null) to stdout with (format csv)" > mbid_mapping.csv
```

## 3. Przefiltruj dump

```bash
python3 scripts/filter_acousticbrainz_dump.py \
  --mapping mbid_mapping.csv \
  --output audio_features.csv \
  acousticbrainz-lowlevel-json-20220623-*.tar.zst
```

## 4. Załaduj do bazy (idempotentnie)

```bash
docker exec -i music-view-postgres psql -U musicview -d musicview <<'SQL'
CREATE TEMP TABLE audio_features_staging (LIKE audio_features INCLUDING DEFAULTS);
\copy audio_features_staging (mbid, spotify_id, bpm, musical_key, danceability) from 'audio_features.csv' with (format csv, null '')
INSERT INTO audio_features (mbid, spotify_id, bpm, musical_key, danceability)
SELECT DISTINCT ON (spotify_id) mbid, spotify_id, bpm, musical_key, danceability
FROM audio_features_staging
ON CONFLICT (mbid) DO NOTHING;
SQL
```

`DISTINCT ON (spotify_id)` respektuje unikalność `audio_features.spotify_id`
(relacja 0..1 do utworu — D17); `ON CONFLICT DO NOTHING` pozwala bezpiecznie
powtórzyć krok.

## 5. Weryfikacja pokrycia

```sql
select count(*)                                   as biblioteka,
       count(af.mbid)                             as z_acousticbrainz,
       round(100.0 * count(af.mbid) / count(*), 1) as pokrycie_pct
from library_entry le
left join audio_features af on af.spotify_id = le.spotify_id;
```

Wynik zasila raport pokrycia BPM (DoD M1.4/M1.9). Resztę braków domyka
kaskada D6: Deezer → LLM (`BpmResolver`).
