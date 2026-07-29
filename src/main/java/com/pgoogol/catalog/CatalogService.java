package com.pgoogol.catalog;

import com.pgoogol.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class CatalogService {

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

    @Transactional(readOnly = true)
    public Page<TrackCatalog> search(CatalogSearchCriteria criteria,
                                     CatalogSortOrder sortOrder,
                                     Pageable pageable) {

        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(sortOrder, "sortOrder");
        String search = Optional.ofNullable(criteria.search())
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .orElse(null);
        return trackCatalogRepository.search(
            search,
            Optional.ofNullable(criteria.genreFamily()).map(Enum::name).orElse(null),
            criteria.bpmMin(),
            criteria.bpmMax(),
            Optional.ofNullable(criteria.tempoClass()).map(Enum::name).orElse(null),
            criteria.energy(),
            criteria.inLibrary(),
            criteria.ratingMin(),
            Optional.ofNullable(criteria.tag())
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .orElse(null),
            sortOrder.field().name(),
            sortOrder.direction().name(),
            pageable);
    }
}
