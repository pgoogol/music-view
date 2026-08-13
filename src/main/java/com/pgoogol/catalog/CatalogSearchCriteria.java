package com.pgoogol.catalog;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Kryteria wyszukiwarki katalogu (M1.7, uporządkowane w M5.6) — wszystko
 * opcjonalne, brak wartości znaczy „bez warunku".
 *
 * <p>Filtry chodzą w grupach, a nie w jednym płaskim rzędzie kilkunastu pól:
 * grupa mówi, <em>o co</em> pytamy ({@link TrackFilter} — sam utwór,
 * {@link SoundFilter} — brzmienie, {@link LibraryFilter} — dane prywatne DJ-a
 * (D3), {@link MetricFilter} — metryki z pliku (D24), {@link QualityFilter} —
 * kompletność danych). Konstruktor z osiemnastoma argumentami tego samego typu
 * nie ma jak wyłapać przestawienia {@code bpmMin} z {@code yearMin} —
 * ten sam argument, który w D36 kazał rozbić kontrakt przeglądu.</p>
 */
public record CatalogSearchCriteria(
    @Nullable String search,
    TrackFilter track,
    SoundFilter sound,
    LibraryFilter library,
    MetricFilter metrics,
    QualityFilter quality) {

    public CatalogSearchCriteria {
        track = Objects.requireNonNullElse(track, TrackFilter.NONE);
        sound = Objects.requireNonNullElse(sound, SoundFilter.NONE);
        library = Objects.requireNonNullElse(library, LibraryFilter.NONE);
        metrics = Objects.requireNonNullElse(metrics, MetricFilter.NONE);
        quality = Objects.requireNonNullElse(quality, QualityFilter.NONE);
    }

    /** Pusty zestaw kryteriów — cały katalog. */
    public static CatalogSearchCriteria none() {
        return new CatalogSearchCriteria(null, null, null, null, null, null);
    }

    /**
     * Filtry samego utworu (D5): gatunek, rocznik, długość, popularność i to,
     * czy tekst jest oznaczony jako explicit — przy weselu to nie ciekawostka,
     * tylko kryterium.
     */
    public record TrackFilter(
        @Nullable GenreFamily genreFamily,
        @Nullable Integer yearMin,
        @Nullable Integer yearMax,
        @Nullable Integer durationMinSec,
        @Nullable Integer durationMaxSec,
        @Nullable Integer popularityMin,
        @Nullable Boolean explicit) {

        public static final TrackFilter NONE =
            new TrackFilter(null, null, null, null, null, null, null);
    }

    /** Filtry brzmienia: tempo, energia i harmonia (D25). */
    public record SoundFilter(
        @Nullable Integer bpmMin,
        @Nullable Integer bpmMax,
        @Nullable TempoClass tempoClass,
        @Nullable String energy,
        @Nullable HarmonicFilter harmonic) {

        public static final SoundFilter NONE = new SoundFilter(null, null, null, null, null);
    }

    /**
     * Filtry po danych prywatnych DJ-a (M3.2): wyszukiwarka chodzi po katalogu
     * (D3), ale potrafi zawęzić go do tego, co jest (albo czego nie ma)
     * w bibliotece.
     */
    public record LibraryFilter(
        @Nullable Boolean inLibrary,
        @Nullable Integer ratingMin,
        @Nullable String tag) {

        public static final LibraryFilter NONE = new LibraryFilter(null, null, null);
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

    /**
     * Filtry kompletności danych (M5.6): skąd wzięło się tempo (pomiar czy
     * estymata LLM — kryterium D19) i czego utworowi brakuje. Dzięki nim badge
     * „do wzbogacenia" z tabeli daje się zamienić w zaznaczenie i wysłać do joba,
     * zamiast szukać braków oczami strona po stronie.
     */
    public record QualityFilter(
        @Nullable BpmSource bpmSource,
        @Nullable MissingGroup missing) {

        public static final QualityFilter NONE = new QualityFilter(null, null);
    }
}
