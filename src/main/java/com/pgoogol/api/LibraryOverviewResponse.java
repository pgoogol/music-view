package com.pgoogol.api;

import java.util.List;

/**
 * Przegląd biblioteki (M4.3) — kontrakt {@code GET /api/library/overview}.
 * Wszystkie rozkłady mają ten sam kształt, więc front rysuje je jednym
 * komponentem zamiast pięciu prawie identycznych.
 */
public record LibraryOverviewResponse(
    long catalogTracks,
    long libraryTracks,
    long tracksWithMetrics,
    long metadataMissing,
    long audioMissing,
    long aiMissing,
    List<BucketResponse> genres,
    List<BucketResponse> tempoClasses,
    List<BucketResponse> energies,
    List<BucketResponse> bpmSources,
    List<BucketResponse> ratings,
    List<BucketResponse> bpmHistogram,
    List<BucketResponse> topArtists,
    List<BucketResponse> monthlyGrowth) {

    public record BucketResponse(String label, long count) { }
}
