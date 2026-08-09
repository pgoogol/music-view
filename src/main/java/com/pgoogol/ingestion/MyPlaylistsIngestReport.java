package com.pgoogol.ingestion;

import java.util.List;

/**
 * Raport importu wszystkich własnych playlist (tryb C): {@code imported} to
 * playlisty domknięte, {@code failed} — te, które padły po drodze. Jedna
 * playlista nie może przerwać całego przebiegu, bo przy kilkudziesięciu
 * playlistach powtarzanie wszystkiego od zera kosztuje kwadranse.
 */
public record MyPlaylistsIngestReport(List<PlaylistIngestReport> imported,
                                      List<FailedPlaylist> failed) {

}
