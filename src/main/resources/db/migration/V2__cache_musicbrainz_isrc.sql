-- V2: trwały cache lookupów MusicBrainz ISRC→MBID (M1.3, D6, D18).
-- mbid NULL = potwierdzony brak wyniku (negative cache) — nie pytamy MB ponownie.

CREATE TABLE musicbrainz_isrc_cache (
    isrc        varchar(16) PRIMARY KEY,
    mbid        varchar(36),
    resolved_at timestamptz NOT NULL
);
