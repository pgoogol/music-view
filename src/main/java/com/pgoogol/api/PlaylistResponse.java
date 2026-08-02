package com.pgoogol.api;

import java.math.BigDecimal;
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

    /**
     * {@code loudnessDb} i {@code timeSignature} pochodzą z metryk wgranych z pliku
     * (D24) i służą wyłącznie ostrzeżeniom planera setu (D25) — skok głośności
     * i metrum inne niż 4/4. Tonacja jedzie w {@code track.camelot}.
     */
    public record PlaylistTrackResponse(
        int position,
        String djSlot,
        String djSlotOverride,
        BigDecimal loudnessDb,
        Integer timeSignature,
        TrackResponse track) {

    }
}
