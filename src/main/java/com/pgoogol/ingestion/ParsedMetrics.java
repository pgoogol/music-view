package com.pgoogol.ingestion;

import com.pgoogol.catalog.GenreFamily;
import org.springframework.lang.Nullable;

/**
 * Poprawny wiersz CSV z metrykami po walidacji (D24). Dopasowanie do katalogu
 * idzie po {@code spotifyId}, a gdy go nie ma (albo nie ma takiego utworu) —
 * po {@code isrc}; przynajmniej jedno z pól jest niepuste.
 *
 * <p>{@code genreFamily} to rodzina wyprowadzona z kolumn z gatunkami — nie jest
 * metryką, więc nie trafia do {@code manual_metrics}; służy tylko do uzupełnienia
 * katalogu, gdy utwór nie był jeszcze wzbogacany przez LLM.</p>
 */
public record ParsedMetrics(
    long line,
    @Nullable String spotifyId,
    @Nullable String isrc,
    @Nullable GenreFamily genreFamily,
    TrackMetrics metrics) {

}
