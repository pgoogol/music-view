package com.pgoogol.enrichment.spotify;

/**
 * Metadane utworu ze Spotify — grupa METADATA (D11); {@code artist} to
 * wszyscy wykonawcy połączeni przecinkami.
 */
public record SpotifyTrackMetadata(
    String spotifyId,
    String title,
    String artist,
    String album,
    Integer year,
    Integer durationMs,
    Integer popularity,
    Boolean explicit,
    String albumImageUrl,
    String isrc) {

}
