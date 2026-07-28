package com.pgoogol.playlist;

import com.pgoogol.common.NotFoundException;
import com.pgoogol.common.ValidationException;
import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Eksport gotowego setu na konto właściciela (M2.4). Pierwszy eksport zakłada
 * playlistę (prywatną) i zapamiętuje jej {@code spotify_playlist_id}; kolejne
 * nadpisują zawartość tej samej playlisty, więc kolejność na Spotify zawsze
 * odpowiada setowi w music-view.
 */
@Service
public class PlaylistExportService {

    private static final String DESCRIPTION = "Set zaplanowany w music-view";
    private static final String PLAYLIST_URL = "https://open.spotify.com/playlist/%s";

    private static final Logger log = LoggerFactory.getLogger(PlaylistExportService.class);

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final SpotifyPlaylistClient playlistClient;
    private final SpotifyAccountService accountService;

    public PlaylistExportService(PlaylistRepository playlistRepository,
                                 PlaylistTrackRepository playlistTrackRepository,
                                 SpotifyPlaylistClient playlistClient,
                                 SpotifyAccountService accountService) {

        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.playlistClient = playlistClient;
        this.accountService = accountService;
    }

    @Transactional
    public PlaylistExport export(Long playlistId) {

        Objects.requireNonNull(playlistId, "playlistId");
        Playlist playlist = playlistRepository.findById(playlistId)
            .orElseThrow(() -> new NotFoundException("PLAYLIST_NOT_FOUND",
                "Playlisty %d nie ma w bazie".formatted(playlistId)));
        List<String> spotifyIds = playlistTrackRepository
            .findAllByPlaylistIdOrderByPositionAsc(playlistId).stream()
            .map(entry -> entry.getTrack().getSpotifyId())
            .toList();
        if (spotifyIds.isEmpty()) {
            throw new ValidationException("PLAYLIST_EMPTY",
                "Set '%s' jest pusty — nie ma czego eksportować".formatted(playlist.getName()));
        }
        String ownerId = accountService.connectedUserId()
            .orElseThrow(() -> new ValidationException("SPOTIFY_NOT_CONNECTED",
                "Konto Spotify nie jest połączone — otwórz /api/auth/spotify/login"));

        boolean created = Objects.isNull(playlist.getSpotifyPlaylistId());
        if (created) {
            playlist.setSpotifyPlaylistId(
                playlistClient.createPlaylist(ownerId, playlist.getName(), DESCRIPTION));
        }
        playlistClient.replaceTracks(playlist.getSpotifyPlaylistId(), spotifyIds);

        log.info("Eksport setu '{}' ({}) na Spotify: {} utworów, playlista {} ({})",
            playlist.getName(), playlistId, spotifyIds.size(), playlist.getSpotifyPlaylistId(),
            created ? "założona" : "nadpisana");
        return new PlaylistExport(playlistId, playlist.getSpotifyPlaylistId(), playlist.getName(),
            spotifyIds.size(), created, PLAYLIST_URL.formatted(playlist.getSpotifyPlaylistId()));
    }
}
