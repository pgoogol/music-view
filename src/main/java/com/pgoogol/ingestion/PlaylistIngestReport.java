package com.pgoogol.ingestion;

import java.util.List;

/**
 * Raport z importu playlisty (M2.1). {@code tracks} to unikalne utwory playlisty
 * (powtórzenia liczone raz), {@code imported} — nowe wpisy biblioteki,
 * {@code alreadyExisted} — utwory już w niej obecne, {@code skipped} — pozycje
 * bez odpowiednika w katalogu Spotify. Kolejność utworów zapisywana w playliście
 * lokalnej niezależnie od tego podziału.
 */
public record PlaylistIngestReport(
    Long playlistId,
    String spotifyPlaylistId,
    String name,
    int tracks,
    int imported,
    int alreadyExisted,
    List<SkippedItem> skipped) {

}
