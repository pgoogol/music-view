package com.pgoogol.api;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Przegląd biblioteki (M4.3, rozbudowa M5.4) — kontrakt
 * {@code GET /api/library/overview}. Pola siedzą w pięciu grupach, bo tyle
 * stref czytania ma ekran przeglądu (D36); rozkłady w obrębie grupy mają
 * wspólny kształt {@link BucketResponse}, więc front rysuje je jednym
 * komponentem zamiast kilkunastu prawie identycznych.
 */
public record LibraryOverviewResponse(
    ScaleResponse scale,
    QualityResponse quality,
    SoundResponse sound,
    TimelineResponse timeline,
    TasteResponse taste,
    List<RecentTrackResponse> recentlyAdded) {

    public record BucketResponse(String label, long count) { }

    public record MatrixCellResponse(String tempoClass, String energy, long count) { }

    public record MetricResponse(String label, double value) { }

    public record RecentTrackResponse(
        String spotifyId,
        @Nullable String title,
        @Nullable String artist,
        @Nullable String albumImageUrl,
        Instant addedAt) { }

    /**
     * Skala zbioru — same utwory (D36); playlisty i sety mają własne zakładki.
     * Średnie są {@code null} dla pustego katalogu, nie zerem.
     */
    public record ScaleResponse(
        long catalogTracks,
        long libraryTracks,
        long tracksWithMetrics,
        long libraryDurationMs,
        long distinctArtists,
        long distinctAlbums,
        @Nullable Double averageBpm,
        @Nullable Double averageDurationMs,
        @Nullable Double averagePopularity,
        @Nullable Double averageDanceability,
        long tracksWithDanceability) { }

    public record QualityResponse(
        long metadataMissing,
        long audioMissing,
        long aiMissing,
        List<BucketResponse> bpmSources,
        List<BucketResponse> confidences) { }

    public record SoundResponse(
        List<BucketResponse> genres,
        List<BucketResponse> styles,
        List<BucketResponse> tempoClasses,
        List<BucketResponse> energies,
        List<BucketResponse> bpmHistogram,
        List<BucketResponse> camelotKeys,
        List<BucketResponse> durations,
        List<BucketResponse> popularity,
        List<BucketResponse> explicitness,
        List<BucketResponse> timeSignatures,
        List<MatrixCellResponse> tempoEnergy,
        List<MetricResponse> audioProfile) { }

    public record TimelineResponse(
        List<BucketResponse> monthlyGrowth,
        List<BucketResponse> decades) { }

    public record TasteResponse(
        List<BucketResponse> topArtists,
        List<BucketResponse> topAlbums,
        List<BucketResponse> topTags,
        List<BucketResponse> ratings) { }
}
