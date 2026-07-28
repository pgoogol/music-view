-- Raport pokrycia per pole i per źródło (M1.9).
-- Uruchomienie: docker exec -i music-view-postgres psql -U musicview -d musicview < scripts/coverage_report.sql

\echo '=== Pokrycie pól (biblioteka) ==='
select count(*)                                                   as utwory,
       round(100.0 * count(t.isrc)            / count(*), 1)      as isrc_pct,
       round(100.0 * count(t.year)            / count(*), 1)      as rok_pct,
       round(100.0 * count(t.duration_ms)     / count(*), 1)      as czas_pct,
       round(100.0 * count(t.album_image_url) / count(*), 1)      as okladka_pct
from library_entry le join track_catalog t on t.spotify_id = le.spotify_id;

select round(100.0 * count(t.bpm)          / count(*), 1) as bpm_pct,
       round(100.0 * count(t.musical_key)  / count(*), 1) as tonacja_pct,
       round(100.0 * count(t.danceability) / count(*), 1) as tanecznosc_pct,
       round(100.0 * count(t.tempo_class)  / count(*), 1) as tempo_pct
from library_entry le join track_catalog t on t.spotify_id = le.spotify_id;

select round(100.0 * count(t.style)          / count(*), 1) as styl_pct,
       round(100.0 * count(t.genre_family)   / count(*), 1) as gatunek_pct,
       round(100.0 * count(t.lyrics_theme)   / count(*), 1) as temat_pct,
       round(100.0 * count(t.description_pl) / count(*), 1) as opis_pct,
       round(100.0 * count(t.energy)         / count(*), 1) as energia_pct
from library_entry le join track_catalog t on t.spotify_id = le.spotify_id;

\echo '=== BPM per źródło (kaskada D6) ==='
select coalesce(t.bpm_source, 'BRAK') as zrodlo_bpm,
       count(*)                       as utwory,
       round(100.0 * count(*) / sum(count(*)) over (), 1) as pct
from library_entry le join track_catalog t on t.spotify_id = le.spotify_id
group by t.bpm_source order by utwory desc;

\echo '=== Komplet pol D5 (DoD: >= 95%) ==='
select count(*) as utwory,
       count(*) filter (where t.isrc is not null and t.year is not null
                          and t.duration_ms is not null
                          and t.bpm is not null and t.tempo_class is not null
                          and t.style is not null and t.genre_family is not null
                          and t.lyrics_theme is not null and t.description_pl is not null
                          and t.energy is not null)                     as komplet,
       round(100.0 * count(*) filter (where t.isrc is not null and t.year is not null
                          and t.duration_ms is not null
                          and t.bpm is not null and t.tempo_class is not null
                          and t.style is not null and t.genre_family is not null
                          and t.lyrics_theme is not null and t.description_pl is not null
                          and t.energy is not null) / count(*), 1)      as komplet_pct
from library_entry le join track_catalog t on t.spotify_id = le.spotify_id;

\echo '=== Rozklad gatunkow i temp ==='
select t.genre_family, count(*) from library_entry le
join track_catalog t on t.spotify_id = le.spotify_id
group by t.genre_family order by count(*) desc;

select t.tempo_class, count(*) from library_entry le
join track_catalog t on t.spotify_id = le.spotify_id
group by t.tempo_class order by count(*) desc;
