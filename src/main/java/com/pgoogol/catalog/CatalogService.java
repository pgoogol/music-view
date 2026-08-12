package com.pgoogol.catalog;

import com.pgoogol.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private static final int MAX_DURATION_SEC = 24 * 60 * 60;

    private final TrackCatalogRepository trackCatalogRepository;
    private final ManualMetricsRepository manualMetricsRepository;

    public CatalogService(TrackCatalogRepository trackCatalogRepository,
                          ManualMetricsRepository manualMetricsRepository) {

        this.trackCatalogRepository = trackCatalogRepository;
        this.manualMetricsRepository = manualMetricsRepository;
    }

    /** Metryki wgrane ręcznie (D24) — pusto, gdy utworu nie ma albo nie dostał metryk. */
    @Transactional(readOnly = true)
    public Optional<ManualMetrics> findMetrics(String spotifyId) {

        Objects.requireNonNull(spotifyId, "spotifyId");
        return manualMetricsRepository.findById(spotifyId);
    }

    @Transactional(readOnly = true)
    public TrackCatalog getTrack(String spotifyId) {

        Objects.requireNonNull(spotifyId, "spotifyId");
        return trackCatalogRepository.findById(spotifyId)
            .orElseThrow(() -> new NotFoundException("TRACK_NOT_FOUND",
                "Brak utworu o spotify_id '%s' w katalogu".formatted(spotifyId)));
    }

    /** Pokrycie katalogu metrykami z pliku (D24) — kontekst dla filtrów z M4.1. */
    @Transactional(readOnly = true)
    public MetricsCoverage metricsCoverage() {

        return new MetricsCoverage(
            trackCatalogRepository.countWithMetrics(), trackCatalogRepository.count());
    }

    @Transactional(readOnly = true)
    public Page<TrackCatalog> search(CatalogSearchCriteria criteria,
                                     CatalogSortOrder sortOrder,
                                     Pageable pageable) {

        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(sortOrder, "sortOrder");
        CatalogSearchCriteria.TrackFilter track = criteria.track();
        CatalogSearchCriteria.SoundFilter sound = criteria.sound();
        CatalogSearchCriteria.LibraryFilter library = criteria.library();
        CatalogSearchCriteria.MetricFilter metrics = criteria.metrics();
        CatalogSearchCriteria.QualityFilter quality = criteria.quality();
        return trackCatalogRepository.search(
            text(criteria.search()),
            name(track.genreFamily()),
            track.yearMin(),
            track.yearMax(),
            millis(track.durationMinSec()),
            millis(track.durationMaxSec()),
            track.popularityMin(),
            track.explicit(),
            sound.bpmMin(),
            sound.bpmMax(),
            name(sound.tempoClass()),
            sound.energy(),
            musicalKeys(sound.harmonic()),
            library.inLibrary(),
            library.ratingMin(),
            text(library.tag()),
            metrics.valenceMin(),
            metrics.valenceMax(),
            metrics.instrumentalMin(),
            metrics.livenessMax(),
            name(quality.bpmSource()),
            name(quality.missing()),
            sortOrder.field().name(),
            sortOrder.direction().name(),
            pageable);
    }

    /** Puste i same białe znaki znaczą „bez filtra", nie „pasuje pusty tekst". */
    @Nullable
    private String text(@Nullable String value) {

        return Optional.ofNullable(value)
            .map(String::strip)
            .filter(stripped -> !stripped.isEmpty())
            .orElse(null);
    }

    @Nullable
    private String name(@Nullable Enum<?> value) {
        return Optional.ofNullable(value).map(Enum::name).orElse(null);
    }

    /**
     * UI pyta o długość w sekundach, kolumna trzyma milisekundy. Doba w sekundach
     * to sufit przeliczenia — bez niego wpisana z palca liczba dziewiątek przekręca
     * {@code int} i filtr zaczyna znaczyć coś zupełnie innego, niż wygląda.
     */
    @Nullable
    private Integer millis(@Nullable Integer seconds) {

        return Optional.ofNullable(seconds)
            .map(value -> Math.clamp(value, 0, MAX_DURATION_SEC) * 1000)
            .orElse(null);
    }

    /**
     * Filtr harmoniczny (D25) rozwiązywany <b>przed</b> zapytaniem: zbiór zgodnych
     * pozycji koła (najwyżej cztery) rozwijamy do wszystkich zapisów tonacji
     * i sklejamy w jeden parametr. Dzięki temu wyszukiwarka zostaje przy dwóch
     * złączeniach, a plan zapytania się nie zmienia.
     */
    private String musicalKeys(@Nullable CatalogSearchCriteria.HarmonicFilter harmonic) {

        if (Objects.isNull(harmonic)) {
            return null;
        }
        Set<CamelotKey> keys = harmonic.compatible()
            ? harmonic.key().compatible()
            : Set.of(harmonic.key());
        return keys.stream()
            .map(CamelotKey::musicalKeySpellings)
            .flatMap(List::stream)
            .distinct()
            .collect(Collectors.joining("|"));
    }

    /** Ile utworów katalogu ma metryki z pliku i ile jest ich w ogóle. */
    public record MetricsCoverage(long withMetrics, long total) { }
}
