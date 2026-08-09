-- V7: teksty utworów, ich tłumaczenie i interpretacja (D32).
--
-- Osobna tabela zamiast kolumn w track_catalog: tekst i tłumaczenie to kilka
-- kilobajtów na utwór, a katalog czyta wyszukiwarka przy każdym przewinięciu
-- listy (2500 wierszy). Relacja 0..1 do utworu, klucz główny = spotify_id,
-- jak w manual_metrics (V5).
--
-- status pełni też rolę negatywnego cache'u (jak musicbrainz_isrc_cache z D18):
-- NOT_FOUND i INSTRUMENTAL to potwierdzone odpowiedzi LRCLIB, więc zakres
-- MISSING nie pyta o nie drugi raz. FETCHED = tekst mamy, tłumaczenia jeszcze
-- nie (LLM padł w trakcie) — taki utwór do zakresu MISSING wraca.

CREATE TABLE track_lyrics (
    spotify_id        varchar(64) PRIMARY KEY REFERENCES track_catalog (spotify_id) ON DELETE CASCADE,
    status            varchar(32) NOT NULL,
    lrclib_id         bigint,
    source_language   varchar(32),
    original_lyrics   text,
    translation_pl    text,
    interpretation_pl text,
    fetched_at        timestamptz,
    translated_at     timestamptz,
    model_used        varchar(128),
    prompt_version    integer
);

-- zakres MISSING i licznik braków filtrują po statusie „rozstrzygnięty"
CREATE INDEX idx_track_lyrics_status ON track_lyrics (status);
