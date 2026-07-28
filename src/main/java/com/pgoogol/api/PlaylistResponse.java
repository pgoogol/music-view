package com.pgoogol.api;

import java.time.Instant;
import java.util.List;

/**
 * Playlista z pełnym składem setu — kontrakt {@code GET /api/playlists/{id}}.
 * {@code djSlot} to slot obowiązujący (override DJ-a albo wyliczenie wg D9),
 * {@code djSlotOverride} pokazuje, czy wartość jest ręczna.
 */
public record PlaylistResponse(
    Long id,
    String name,
    String spotifyPlaylistId,
    Instant createdAt,
    List<PlaylistTrackResponse> tracks) {

    public record PlaylistTrackResponse(
        int position,
        String djSlot,
        String djSlotOverride,
        TrackResponse track) {

    }
}
