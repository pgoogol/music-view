package com.pgoogol.api;

import java.time.Instant;

/**
 * Tekst utworu z tłumaczeniem i interpretacją (M6.1/D32) — kontrakt
 * {@code GET /api/catalog/tracks/{spotifyId}/lyrics}.
 *
 * <p>{@code status} niesie odpowiedź także wtedy, gdy tekstu nie ma:
 * {@code NOT_FOUND} (LRCLIB nie zna nagrania) i {@code INSTRUMENTAL} to
 * rozstrzygnięcia, a nie awarie — front pokazuje je wprost zamiast wiecznego
 * „ładowanie".</p>
 */
public record TrackLyricsResponse(
    String spotifyId,
    String status,
    String sourceLanguage,
    String originalLyrics,
    String translationPl,
    String interpretationPl,
    Long lrclibId,
    Instant fetchedAt,
    Instant translatedAt,
    String modelUsed,
    Integer promptVersion) {

}
