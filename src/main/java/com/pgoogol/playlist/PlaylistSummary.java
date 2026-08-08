package com.pgoogol.playlist;

import java.time.Instant;

/**
 * Playlista na liście — bez utworów, za to z ich liczbą (projekcja read-only).
 */
public record PlaylistSummary(
    Long id,
    String name,
    String spotifyPlaylistId,
    Instant createdAt,
    long trackCount,
    int version) {

}
