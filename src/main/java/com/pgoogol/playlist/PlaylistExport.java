package com.pgoogol.playlist;

/**
 * Wynik eksportu setu na Spotify (M2.4). {@code created} odróżnia pierwszy
 * eksport (playlista założona) od nadpisania zawartości istniejącej.
 */
public record PlaylistExport(
    Long playlistId,
    String spotifyPlaylistId,
    String name,
    int exportedTracks,
    boolean created,
    String spotifyUrl) {

}
