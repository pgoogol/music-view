package com.pgoogol.api;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Przegląd biblioteki (M4.3, rozbudowa M5.4) — kontrakt
 * {@code GET /api/library/overview}. Pola siedzą w pięciu grupach, bo tyle
 * stref czytania ma ekran przeglądu (D32); rozkłady w obrębie grupy mają
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

    /** {@code averageBpm} jest {@code null} dla pustego katalogu, nie zerem. */
    public record ScaleResponse(
        long catalogTracks,
        long libraryTracks,
        long tracksWithMetrics,
        long libraryDurationMs,
        long distinctArtists,
        @Nullable Double averageBpm,
        long playlists,
        long tracksInPlaylists,
        long tracksOutsidePlaylists) { }

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
        List<MatrixCellResponse> tempoEnergy,
        List<MetricResponse> audioProfile) { }

    public record TimelineResponse(
        List<BucketResponse> monthlyGrowth,
        List<BucketResponse> decades) { }

    public record TasteResponse(
        List<BucketResponse> topArtists,
        List<BucketResponse> topTags,
        List<BucketResponse> ratings,
        List<BucketResponse> sources) { }
}
