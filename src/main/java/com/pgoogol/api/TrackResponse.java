package com.pgoogol.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pełny rekord katalogu (D5) — kontrakt Catalog API.
 */
public record TrackResponse(
    String spotifyId,
    String title,
    String artist,
    String album,
    Integer year,
    Integer durationMs,
    Integer popularity,
    Boolean explicit,
    String albumImageUrl,
    String isrc,
    String genreFamily,
    String style,
    Integer bpm,
    String bpmSource,
    BigDecimal danceability,
    String musicalKey,
    String tempoClass,
    String energy,
    String lyricsTheme,
    String descriptionPl,
    String confidence,
    Instant enrichedAt,
    String modelUsed,
    Integer enrichVersion) {

}
