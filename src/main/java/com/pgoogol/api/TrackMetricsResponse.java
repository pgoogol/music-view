package com.pgoogol.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Metryki wgrane ręcznie (D24) — kontrakt
 * {@code GET /api/catalog/tracks/{spotifyId}/metrics}. Cechy w skali 0..1,
 * {@code bpm} bez korekty half-time (skorygowana wartość jest w katalogu).
 */
public record TrackMetricsResponse(
    String spotifyId,
    BigDecimal bpm,
    String musicalKey,
    String camelot,
    BigDecimal danceability,
    BigDecimal energy,
    BigDecimal valence,
    BigDecimal acousticness,
    BigDecimal instrumentalness,
    BigDecimal speechiness,
    BigDecimal liveness,
    BigDecimal loudnessDb,
    Integer timeSignature,
    String source,
    Instant importedAt) {

}
