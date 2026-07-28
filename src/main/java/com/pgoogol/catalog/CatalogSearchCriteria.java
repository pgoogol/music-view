package com.pgoogol.catalog;

import org.springframework.lang.Nullable;

/**
 * Kryteria wyszukiwarki katalogu (M1.7) — wszystkie pola opcjonalne.
 */
public record CatalogSearchCriteria(
    @Nullable String search,
    @Nullable GenreFamily genreFamily,
    @Nullable Integer bpmMin,
    @Nullable Integer bpmMax,
    @Nullable TempoClass tempoClass,
    @Nullable String energy) {

}
