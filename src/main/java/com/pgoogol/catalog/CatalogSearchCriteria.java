package com.pgoogol.catalog;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Kryteria wyszukiwarki katalogu (M1.7) — wszystkie pola opcjonalne.
 *
 * <p>{@code inLibrary}/{@code ratingMin}/{@code tag} pochodzą z biblioteki DJ-a
 * (M3.2): wyszukiwarka chodzi po katalogu (D3), ale filtruje go danymi
 * prywatnymi. {@code harmonic} i {@code metrics} doszły w M4.1 (D25).</p>
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
    @Nullable String tag,
    @Nullable HarmonicFilter harmonic,
    MetricFilter metrics) {

    public CatalogSearchCriteria {
        metrics = Objects.requireNonNullElse(metrics, MetricFilter.NONE);
    }

    /**
     * Filtr harmoniczny (D25): pozycja koła Camelot i to, czy zawęzić do niej
     * samej, czy rozszerzyć do zbioru zgodnych (ta sama tonacja, ±1, równoległa).
     */
    public record HarmonicFilter(CamelotKey key, boolean compatible) {

        public HarmonicFilter {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * Filtry po metrykach wgranych z pliku (D24/D25). Działają wyłącznie dla
     * utworów, które te metryki dostały — utwór bez wiersza w {@code manual_metrics}
     * wypada z wyniku, bo porównanie z NULL-em nie jest prawdziwe. To celowe,
     * ale UI musi to mówić wprost licznikiem pokrycia, inaczej pusty wynik
     * wygląda jak awaria.
     */
    public record MetricFilter(
        @Nullable BigDecimal valenceMin,
        @Nullable BigDecimal valenceMax,
        @Nullable BigDecimal instrumentalMin,
        @Nullable BigDecimal livenessMax) {

        public static final MetricFilter NONE = new MetricFilter(null, null, null, null);
    }
}
