package com.pgoogol.ingestion;

/**
 * Playlista, której nie udało się zaimportować przy imporcie hurtem (tryb C).
 * {@code errorCode} jest maszynowy (jak w błędach API), {@code reason} — do
 * pokazania DJ-owi, żeby wiedział, którą playlistę powtórzyć po linku.
 */
public record FailedPlaylist(String spotifyPlaylistId, String name, String errorCode, String reason) {

}
