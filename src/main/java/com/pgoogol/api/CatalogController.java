package com.pgoogol.api;

import com.pgoogol.catalog.CatalogSearchCriteria;
import com.pgoogol.catalog.CatalogService;
import com.pgoogol.catalog.CatalogSort;
import com.pgoogol.catalog.CatalogSortOrder;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TempoClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/tracks")
    @Operation(summary = "Wyszukiwarka katalogu",
        description = "Pełnotekstowo (tsvector) + fuzzy (pg_trgm) po tytule/wykonawcy; "
            + "filtry: genreFamily, bpmMin/bpmMax, tempoClass, energy; "
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
            @RequestParam(required = false) CatalogSort sort,
            @RequestParam(required = false) Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        CatalogSearchCriteria criteria =
            new CatalogSearchCriteria(search, genreFamily, bpmMin, bpmMax, tempoClass, energy);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), cappedSize(size));
        return PageResponse.of(
            catalogService.search(criteria, CatalogSortOrder.of(sort, direction), pageRequest),
            mapper::toResponse);
    }

    static int cappedSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
