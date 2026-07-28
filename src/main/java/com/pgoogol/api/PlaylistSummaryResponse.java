package com.pgoogol.api;

import java.time.Instant;

/**
 * Playlista na liście — kontrakt {@code GET /api/playlists}.
 */
public record PlaylistSummaryResponse(
    Long id,
    String name,
    String spotifyPlaylistId,
    Instant createdAt,
    long trackCount) {

}
