package com.pgoogol.ingestion;

import com.pgoogol.common.AppException;
import com.pgoogol.common.ValidationException;
import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.library.LibrarySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Import wszystkich własnych playlist właściciela (M2.2, tryb C). Playlisty
 * obserwowane, ale cudze, są pomijane — te importuje się pojedynczo po linku
 * (tryb D). Każda playlista idzie w osobnej transakcji {@link PlaylistIngestionService},
 * a jej awaria trafia do raportu zamiast przerywać przebieg — powtórzenie
 * importu jest bezpieczne, więc nieudane playlisty wystarczy powtórzyć.
 */
@Service
public class MyPlaylistsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MyPlaylistsIngestionService.class);

    private final SpotifyAccountService accountService;
    private final SpotifyPlaylistClient playlistClient;
    private final PlaylistIngestionService playlistIngestionService;

    public MyPlaylistsIngestionService(SpotifyAccountService accountService,
                                       SpotifyPlaylistClient playlistClient,
                                       PlaylistIngestionService playlistIngestionService) {

        this.accountService = accountService;
        this.playlistClient = playlistClient;
        this.playlistIngestionService = playlistIngestionService;
    }

    public MyPlaylistsIngestReport ingestMyPlaylists() {

        String ownerId = accountService.connectedUserId()
            .orElseThrow(() -> new ValidationException("SPOTIFY_NOT_CONNECTED",
                "Konto Spotify nie jest połączone — otwórz /api/auth/spotify/login"));
        List<SpotifyPlaylist> owned = playlistClient.getMyPlaylists().stream()
            .filter(playlist -> Objects.equals(playlist.ownerId(), ownerId))
            .toList();
        log.info("Import własnych playlist konta {}: {} do zaimportowania", ownerId, owned.size());

        List<PlaylistIngestReport> imported = new ArrayList<>();
        List<FailedPlaylist> failed = new ArrayList<>();
        owned.forEach(playlist -> ingestOne(playlist, imported, failed));

        log.info("Import własnych playlist konta {} zakończony: zaimportowane={}, nieudane={}",
            ownerId, imported.size(), failed.size());
        return new MyPlaylistsIngestReport(List.copyOf(imported), List.copyOf(failed));
    }

    /**
     * Każda playlista idzie osobno i osobno może paść — wygasły token, utwór
     * bez odpowiednika, chwilowe 5xx ze Spotify. Awaria trafia do raportu
     * i przebieg leci dalej: import kilkudziesięciu playlist jest zbyt drogi,
     * żeby wywracać go na jednej.
     */
    private void ingestOne(SpotifyPlaylist playlist, List<PlaylistIngestReport> imported,
                           List<FailedPlaylist> failed) {

        try {
            imported.add(playlistIngestionService.ingest(playlist, LibrarySource.PLAYLIST));
        } catch (AppException ex) {
            log.warn("Playlista '{}' ({}) pominięta: {} — {}", playlist.name(),
                playlist.spotifyPlaylistId(), ex.getErrorCode(), ex.getMessage());
            failed.add(new FailedPlaylist(playlist.spotifyPlaylistId(), playlist.name(),
                ex.getErrorCode(), ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("Playlista '{}' ({}) pominięta — nieoczekiwany błąd",
                playlist.name(), playlist.spotifyPlaylistId(), ex);
            failed.add(new FailedPlaylist(playlist.spotifyPlaylistId(), playlist.name(),
                "INTERNAL_ERROR", "nieoczekiwany błąd importu — szczegóły w logach aplikacji"));
        }
    }
}
