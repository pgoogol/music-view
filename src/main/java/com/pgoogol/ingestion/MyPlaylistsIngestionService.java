package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.library.LibrarySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Import wszystkich własnych playlist właściciela (M2.2, tryb C). Playlisty
 * obserwowane, ale cudze, są pomijane — te importuje się pojedynczo po linku
 * (tryb D). Każda playlista idzie w osobnej transakcji {@link PlaylistIngestionService},
 * więc przerwany import zostawia domknięte playlisty, a powtórzenie jest bezpieczne.
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

    public List<PlaylistIngestReport> ingestMyPlaylists() {

        String ownerId = accountService.connectedUserId()
            .orElseThrow(() -> new ValidationException("SPOTIFY_NOT_CONNECTED",
                "Konto Spotify nie jest połączone — otwórz /api/auth/spotify/login"));
        List<SpotifyPlaylist> owned = playlistClient.getMyPlaylists().stream()
            .filter(playlist -> Objects.equals(playlist.ownerId(), ownerId))
            .toList();
        log.info("Import własnych playlist konta {}: {} do zaimportowania", ownerId, owned.size());
        return owned.stream()
            .map(playlist -> playlistIngestionService.ingest(playlist, LibrarySource.PLAYLIST))
            .toList();
    }
}
