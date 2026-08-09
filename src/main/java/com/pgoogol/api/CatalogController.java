package com.pgoogol.api;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.catalog.CatalogSearchCriteria;
import com.pgoogol.catalog.CatalogSearchCriteria.HarmonicFilter;
import com.pgoogol.catalog.CatalogSearchCriteria.MetricFilter;
import com.pgoogol.catalog.CatalogService;
import com.pgoogol.catalog.CatalogSort;
import com.pgoogol.catalog.CatalogSortOrder;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.common.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Objects;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog", description = "Katalog utworów — dane deterministyczne (D3)")
public class CatalogController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final CatalogService catalogService;
    private final CatalogApiMapper mapper;

    public CatalogController(CatalogService catalogService, CatalogApiMapper mapper) {

        this.catalogService = catalogService;
        this.mapper = mapper;
    }

    @GetMapping("/tracks/{spotifyId}")
    @Operation(summary = "Pełny rekord utworu z katalogu")
    public TrackResponse getTrack(@PathVariable String spotifyId) {
        return mapper.toResponse(catalogService.getTrack(spotifyId));
    }

    @GetMapping("/tracks/{spotifyId}/metrics")
    @Operation(summary = "Metryki utworu wgrane ręcznie z CSV (D24)",
        description = "Surowe wartości z pliku: cechy w skali 0..1, BPM bez korekty half-time. "
            + "204, gdy utwór nie dostał jeszcze metryk.")
    public ResponseEntity<TrackMetricsResponse> getTrackMetrics(@PathVariable String spotifyId) {

        return catalogService.findMetrics(spotifyId)
            .map(mapper::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/tracks/{spotifyId}/lyrics")
    @Operation(summary = "Tekst utworu z tłumaczeniem i interpretacją (D32)",
        description = "Tekst pochodzi z LRCLIB, tłumaczenie i interpretacja z modelu "
            + "wskazanego w konfiguracji (D15). 204, gdy utwór nie przeszedł jeszcze grupy "
            + "pól LYRICS; status NOT_FOUND/INSTRUMENTAL to odpowiedź LRCLIB, nie brak danych.")
    public ResponseEntity<TrackLyricsResponse> getTrackLyrics(@PathVariable String spotifyId) {

        return catalogService.findLyrics(spotifyId)
            .map(mapper::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/metrics-coverage")
    @Operation(summary = "Ile utworów katalogu ma metryki z pliku (D24)",
        description = "Kontekst dla filtrów valenceMin/valenceMax/instrumentalMin/livenessMax — "
            + "działają wyłącznie na utworach z metrykami, więc UI musi pokazać pokrycie.")
    public MetricsCoverageResponse getMetricsCoverage() {

        CatalogService.MetricsCoverage coverage = catalogService.metricsCoverage();
        return new MetricsCoverageResponse(coverage.withMetrics(), coverage.total());
    }

    @GetMapping("/tracks")
    @Operation(summary = "Wyszukiwarka katalogu",
        description = "Pełnotekstowo (tsvector) + fuzzy (pg_trgm) po tytule/wykonawcy; "
            + "filtry katalogu: genreFamily, bpmMin/bpmMax, tempoClass, energy; "
            + "filtry biblioteki DJ-a (D3): inLibrary (true = tylko z biblioteki, "
            + "false = tylko spoza), ratingMin, tag; "
            + "filtr harmoniczny (D25): camelot (np. 8A) + camelotCompatible "
            + "(true = także sąsiedzi na kole i tonacja równoległa); "
            + "filtry metryk (D24): valenceMin/valenceMax, instrumentalMin, livenessMax — "
            + "odsiewają utwory bez metryk, por. /api/catalog/metrics-coverage; "
            + "sortowanie: sort (RELEVANCE domyślnie, TITLE, ARTIST, YEAR, BPM, POPULARITY, "
            + "DURATION, ENERGY) + direction (ASC/DESC), braki zawsze na końcu; "
            + "paginacja (max 100).")
    public PageResponse<TrackResponse> searchTracks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) GenreFamily genreFamily,
            @RequestParam(required = false) Integer bpmMin,
            @RequestParam(required = false) Integer bpmMax,
            @RequestParam(required = false) TempoClass tempoClass,
            @RequestParam(required = false) String energy,
            @RequestParam(required = false) Boolean inLibrary,
            @RequestParam(required = false) Integer ratingMin,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String camelot,
            @RequestParam(defaultValue = "true") boolean camelotCompatible,
            @RequestParam(required = false) BigDecimal valenceMin,
            @RequestParam(required = false) BigDecimal valenceMax,
            @RequestParam(required = false) BigDecimal instrumentalMin,
            @RequestParam(required = false) BigDecimal livenessMax,
            @RequestParam(required = false) CatalogSort sort,
            @RequestParam(required = false) Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        CatalogSearchCriteria criteria = new CatalogSearchCriteria(
            search, genreFamily, bpmMin, bpmMax, tempoClass, energy, inLibrary, ratingMin, tag,
            harmonicFilter(camelot, camelotCompatible),
            new MetricFilter(valenceMin, valenceMax, instrumentalMin, livenessMax));
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), cappedSize(size));
        return PageResponse.of(
            catalogService.search(criteria, CatalogSortOrder.of(sort, direction), pageRequest),
            mapper::toResponse);
    }

    private HarmonicFilter harmonicFilter(String camelot, boolean compatible) {

        if (Objects.isNull(camelot) || camelot.isBlank()) {
            return null;
        }
        return CamelotKey.ofLabel(camelot)
            .map(key -> new HarmonicFilter(key, compatible))
            .orElseThrow(() -> new ValidationException("INVALID_CAMELOT",
                "Nieprawidłowa pozycja koła Camelot: '%s' (oczekiwano 1A–12B)".formatted(camelot)));
    }

    static int cappedSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
