package com.pgoogol.playlist;

import java.util.List;

/**
 * Playlista z utworami w kolejności setu i slotami wieczoru (M2.3).
 */
public record PlaylistPlan(Playlist playlist, List<PlannedTrack> tracks) {

}
