package com.pgoogol.api;

import com.pgoogol.enrichment.EnrichmentService;
import com.pgoogol.enrichment.MissingFieldsCount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrich")
@Tag(name = "Enrichment", description = "Joby wzbogacania Spring Batch (D10)")
public class EnrichController {

    private final EnrichmentService enrichmentService;

    public EnrichController(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Zlecenie wzbogacania (asynchroniczne)",
        description = "scope: SINGLE/SELECTED (z spotifyIds, max 100) lub MISSING (wg braków); "
            + "fields: podzbiór METADATA/AUDIO/AI.")
    public Map<String, Long> startEnrichment(@Valid @RequestBody EnrichRequest request) {

        long executionId = enrichmentService.start(
            request.scope(), request.fields(), request.spotifyIdsOrEmpty());
        return Map.of("executionId", executionId);
    }

    @GetMapping("/jobs")
    @Operation(summary = "Ostatnie wykonania jobów wzbogacania")
    public List<EnrichJobResponse> listJobs(@RequestParam(defaultValue = "20") int limit) {

        return enrichmentService.listJobs(CatalogController.cappedSize(limit)).stream()
            .map(EnrichJobResponse::from)
            .toList();
    }

    @GetMapping("/jobs/{executionId}")
    @Operation(summary = "Status i postęp wykonania joba")
    public EnrichJobResponse jobStatus(@PathVariable long executionId) {
        return EnrichJobResponse.from(enrichmentService.status(executionId));
    }

    @PostMapping("/jobs/{executionId}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Restart nieudanego wykonania — dokańcza od checkpointu")
    public Map<String, Long> restartJob(@PathVariable long executionId) {
        return Map.of("executionId", enrichmentService.restart(executionId));
    }

    @GetMapping("/missing-count")
    @Operation(summary = "Liczba utworów z brakami per grupa pól (D11)")
    public MissingFieldsCount missingCount() {
        return enrichmentService.missingCount();
    }
}
