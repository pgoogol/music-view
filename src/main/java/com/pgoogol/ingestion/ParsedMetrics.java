package com.pgoogol.ingestion;

import org.springframework.lang.Nullable;

/**
 * Poprawny wiersz CSV z metrykami po walidacji (D24). Dopasowanie do katalogu
 * idzie po {@code spotifyId}, a gdy go nie ma (albo nie ma takiego utworu) —
 * po {@code isrc}; przynajmniej jedno z pól jest niepuste.
 */
public record ParsedMetrics(
    long line,
    @Nullable String spotifyId,
    @Nullable String isrc,
    TrackMetrics metrics) {

}
