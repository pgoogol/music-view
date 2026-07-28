package com.pgoogol.ingestion;

/**
 * Pominięta pozycja playlisty; {@code position} liczone od 0 w skali całej
 * playlisty (tak jak widać ją na Spotify).
 */
public record SkippedItem(int position, String reason) {

}
