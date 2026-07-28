package com.pgoogol.enrichment.spotify;

/**
 * Nagłówek playlisty Spotify (M2.1). {@code ownerId} rozstrzyga, czy playlista
 * należy do właściciela biblioteki (source=PLAYLIST) czy jest cudza
 * (source=FOREIGN_PLAYLIST).
 */
public record SpotifyPlaylist(
    String spotifyPlaylistId,
    String name,
    String ownerId,
    String ownerDisplayName,
    int trackCount) {

}
