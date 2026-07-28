-- V1: bazowy schemat wg ERD z docs/PLAN.md; doprecyzowania w DECYZJE.md D17.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Dane deterministyczne utworu (D3): metadane + cechy audio + analiza AI.
CREATE TABLE track_catalog (
    spotify_id      varchar(64)  PRIMARY KEY,
    isrc            varchar(16),
    title           varchar(500),
    artist          varchar(500),
    album           varchar(500),
    year            integer,
    duration_ms     integer,
    popularity      integer,
    explicit        boolean,
    album_image_url varchar(1024),
    genre_family    varchar(32),
    style           varchar(255),
    bpm             integer,
    bpm_source      varchar(32),
    danceability    numeric(6,3),
    musical_key     varchar(16),
    tempo_class     varchar(16),
    energy          varchar(32),
    lyrics_theme    varchar(500),
    description_pl  text,
    confidence      varchar(16),
    enriched_at     timestamptz,
    model_used      varchar(128),
    enrich_version  integer,
    search_vector   tsvector GENERATED ALWAYS AS (
        to_tsvector('simple',
            coalesce(title, '') || ' ' || coalesce(artist, '') || ' ' || coalesce(album, ''))
    ) STORED
);

CREATE INDEX idx_track_catalog_title_trgm   ON track_catalog USING gin (title gin_trgm_ops);
CREATE INDEX idx_track_catalog_artist_trgm  ON track_catalog USING gin (artist gin_trgm_ops);
CREATE INDEX idx_track_catalog_search       ON track_catalog USING gin (search_vector);
CREATE INDEX idx_track_catalog_bpm          ON track_catalog (bpm);
CREATE INDEX idx_track_catalog_genre_family ON track_catalog (genre_family);
CREATE INDEX idx_track_catalog_isrc         ON track_catalog (isrc);

-- Cechy audio z dumpa AcousticBrainz (D7); 0..1 na utwór.
CREATE TABLE audio_features (
    mbid         varchar(36) PRIMARY KEY,
    spotify_id   varchar(64) NOT NULL UNIQUE REFERENCES track_catalog (spotify_id),
    bpm          numeric(6,2),
    musical_key  varchar(16),
    danceability numeric(6,3)
);

-- Dane prywatne DJ-a (D3); jeden wpis na utwór (dedup — D17).
CREATE TABLE library_entry (
    id               bigserial PRIMARY KEY,
    spotify_id       varchar(64) NOT NULL UNIQUE REFERENCES track_catalog (spotify_id),
    source           varchar(32) NOT NULL,
    added_at         timestamptz NOT NULL,
    dj_notes         text,
    custom_tags      text[],
    dj_slot_override varchar(32),
    rating           integer
);

CREATE TABLE playlist (
    id                  bigserial PRIMARY KEY,
    name                varchar(255) NOT NULL,
    spotify_playlist_id varchar(64),
    created_at          timestamptz NOT NULL
);

CREATE TABLE playlist_track (
    id          bigserial   PRIMARY KEY,
    playlist_id bigint      NOT NULL REFERENCES playlist (id) ON DELETE CASCADE,
    spotify_id  varchar(64) NOT NULL REFERENCES track_catalog (spotify_id),
    position    integer     NOT NULL,
    CONSTRAINT uq_playlist_track UNIQUE (playlist_id, spotify_id)
);

CREATE INDEX idx_playlist_track_playlist ON playlist_track (playlist_id);
