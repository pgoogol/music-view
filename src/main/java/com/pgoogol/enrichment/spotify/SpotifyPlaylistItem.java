package com.pgoogol.enrichment.spotify;

/**
 * Pozycja playlisty Spotify: albo utwór z katalogu, albo pozycja, której
 * nie da się zaimportować (plik lokalny DJ-a, odcinek podcastu, utwór usunięty
 * lub niedostępny w regionie). {@code position} liczone od 0 w skali całej
 * playlisty — także po stronicowaniu.
 */
public sealed interface SpotifyPlaylistItem {

    int position();

    record Track(int position, SpotifyTrackMetadata metadata) implements SpotifyPlaylistItem {

    }

    record Unavailable(int position, String reason) implements SpotifyPlaylistItem {

    }
}
