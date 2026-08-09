package com.pgoogol.ingestion;

import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.common.ValidationException;
import com.pgoogol.enrichment.spotify.SpotifyAccountService;
import com.pgoogol.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.library.LibrarySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Import własnych playlist (tryb C) — awaria pojedynczej playlisty nie może
 * przerwać przebiegu, bo przy kilkudziesięciu playlistach powtarzanie całości
 * kosztuje kwadranse.
 */
@ExtendWith(MockitoExtension.class)
class MyPlaylistsIngestionServiceTest {

    private static final String OWNER_ID = "dj-pgoogol";

    @Mock
    private SpotifyAccountService accountService;

    @Mock
    private SpotifyPlaylistClient playlistClient;

    @Mock
    private PlaylistIngestionService playlistIngestionService;

    @InjectMocks
    private MyPlaylistsIngestionService service;

    @Test
    void ingestMyPlaylists_whenOnePlaylistFails_importsTheRestAndReportsIt() {

        // given
        SpotifyPlaylist wesela = playlist("pl-wesela", "Wesela 2026");
        SpotifyPlaylist bachata = playlist("pl-bachata", "Bachata");
        SpotifyPlaylist salsa = playlist("pl-salsa", "Salsa nocą");
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));
        given(playlistClient.getMyPlaylists()).willReturn(List.of(wesela, bachata, salsa));
        given(playlistIngestionService.ingest(wesela, LibrarySource.PLAYLIST))
            .willReturn(report("pl-wesela", "Wesela 2026"));
        given(playlistIngestionService.ingest(bachata, LibrarySource.PLAYLIST))
            .willThrow(new ExternalServiceException("SPOTIFY_UNAVAILABLE", "Spotify nie odpowiedziało"));
        given(playlistIngestionService.ingest(salsa, LibrarySource.PLAYLIST))
            .willReturn(report("pl-salsa", "Salsa nocą"));

        // when
        MyPlaylistsIngestReport result = service.ingestMyPlaylists();

        // then — playlista po felernej też wchodzi
        assertThat(result.imported()).extracting(PlaylistIngestReport::name)
            .containsExactly("Wesela 2026", "Salsa nocą");
        assertThat(result.failed()).singleElement().satisfies(failed -> {
            assertThat(failed.spotifyPlaylistId()).isEqualTo("pl-bachata");
            assertThat(failed.name()).isEqualTo("Bachata");
            assertThat(failed.errorCode()).isEqualTo("SPOTIFY_UNAVAILABLE");
            assertThat(failed.reason()).isEqualTo("Spotify nie odpowiedziało");
        });
    }

    @Test
    void ingestMyPlaylists_whenPlaylistFailsUnexpectedly_hidesInternalsBehindErrorCode() {

        // given
        SpotifyPlaylist wesela = playlist("pl-wesela", "Wesela 2026");
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));
        given(playlistClient.getMyPlaylists()).willReturn(List.of(wesela));
        given(playlistIngestionService.ingest(wesela, LibrarySource.PLAYLIST))
            .willThrow(new IllegalStateException("ERROR: relation \"playlist\" does not exist"));

        // when
        MyPlaylistsIngestReport result = service.ingestMyPlaylists();

        // then — komunikat bazy nie wychodzi na zewnątrz (docs/rules/errorhandling.md)
        assertThat(result.imported()).isEmpty();
        assertThat(result.failed()).singleElement().satisfies(failed -> {
            assertThat(failed.errorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(failed.reason()).doesNotContain("relation");
        });
    }

    @Test
    void ingestMyPlaylists_whenPlaylistIsForeign_skipsItWithoutImporting() {

        // given
        SpotifyPlaylist foreign = new SpotifyPlaylist("pl-cudza", "Cudza salsa", "ktos-inny", "Ktoś", 1);
        given(accountService.connectedUserId()).willReturn(Optional.of(OWNER_ID));
        given(playlistClient.getMyPlaylists()).willReturn(List.of(foreign));

        // when
        MyPlaylistsIngestReport result = service.ingestMyPlaylists();

        // then
        assertThat(result.imported()).isEmpty();
        assertThat(result.failed()).isEmpty();
        verify(playlistIngestionService, org.mockito.Mockito.never()).ingest(any(), any());
    }

    @Test
    void ingestMyPlaylists_whenAccountNotConnected_failsBeforeAnyImport() {

        // given
        given(accountService.connectedUserId()).willReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> service.ingestMyPlaylists())
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("nie jest połączone");
    }

    private SpotifyPlaylist playlist(String spotifyPlaylistId, String name) {

        return new SpotifyPlaylist(spotifyPlaylistId, name, OWNER_ID, "DJ pgoogol", 10);
    }

    private PlaylistIngestReport report(String spotifyPlaylistId, String name) {

        return new PlaylistIngestReport(1L, spotifyPlaylistId, name, 10, 10, 0, List.of());
    }
}
