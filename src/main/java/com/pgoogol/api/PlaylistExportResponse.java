package com.pgoogol.api;

/**
 * Wynik eksportu setu — kontrakt {@code POST /api/playlists/{id}/export-to-spotify}.
 */
public record PlaylistExportResponse(
    Long playlistId,
    String spotifyPlaylistId,
    String name,
    int exportedTracks,
    boolean created,
    String spotifyUrl) {

}
