package com.pgoogol.catalog;

import org.springframework.lang.Nullable;

/**
 * Kryteria wyszukiwarki katalogu (M1.7) — wszystkie pola opcjonalne.
 *
 * <p>Trzy ostatnie pola pochodzą z biblioteki DJ-a (M3.2): wyszukiwarka chodzi
 * po katalogu (D3), ale filtruje go danymi prywatnymi — {@code inLibrary}
 * (true = tylko utwory z biblioteki, false = tylko spoza), {@code ratingMin}
 * (ocena co najmniej) i {@code tag} (custom tag DJ-a).
 */
public record CatalogSearchCriteria(
    @Nullable String search,
    @Nullable GenreFamily genreFamily,
    @Nullable Integer bpmMin,
    @Nullable Integer bpmMax,
    @Nullable TempoClass tempoClass,
    @Nullable String energy,
    @Nullable Boolean inLibrary,
    @Nullable Integer ratingMin,
    @Nullable String tag) {

    /** Kryteria bez filtrów bibliotecznych — skrót dla wywołań sprzed M3.2. */
    public CatalogSearchCriteria(@Nullable String search,
                                 @Nullable GenreFamily genreFamily,
                                 @Nullable Integer bpmMin,
                                 @Nullable Integer bpmMax,
                                 @Nullable TempoClass tempoClass,
                                 @Nullable String energy) {

        this(search, genreFamily, bpmMin, bpmMax, tempoClass, energy, null, null, null);
    }
}
