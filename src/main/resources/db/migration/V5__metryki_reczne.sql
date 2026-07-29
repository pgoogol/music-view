-- V5: metryki wgrywane ręcznie z CSV (D24) — surowe fakty audio spoza API,
-- klucz = spotify_id, 0..1 rekord na utwór katalogu. Projekcja na track_catalog
-- (bpm/bpm_source/musical_key/danceability/tempo_class/energy) robi aplikacja.

CREATE TABLE manual_metrics (
    spotify_id       varchar(64) PRIMARY KEY REFERENCES track_catalog (spotify_id) ON DELETE CASCADE,
    bpm              numeric(6,2),
    musical_key      varchar(16),
    camelot          varchar(8),
    danceability     numeric(6,3),
    energy           numeric(6,3),
    valence          numeric(6,3),
    acousticness     numeric(6,3),
    instrumentalness numeric(6,3),
    speechiness      numeric(6,3),
    liveness         numeric(6,3),
    loudness_db      numeric(5,2),
    time_signature   integer,
    source           varchar(255),
    imported_at      timestamptz NOT NULL
);
