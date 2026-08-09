package com.pgoogol.ingestion;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.ExternalServiceException;
import com.pgoogol.enrichment.spotify.SpotifyAccount;
import com.pgoogol.enrichment.spotify.SpotifyAccountRepository;
import com.pgoogol.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistItem;
import com.pgoogol.enrichment.spotify.SpotifyTrackMetadata;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import com.pgoogol.playlist.PlaylistRepository;
import com.pgoogol.playlist.PlaylistTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tryb C (DoD M2.2): import wszystkich własnych playlist połączonego konta —
 * playlisty obserwowane, ale cudze, są pomijane.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IngestMyPlaylistsIntegrationTest {

    private static final String OWNER_ID = "dj-pgoogol";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpotifyAccountRepository accountRepository;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @MockitoBean
    private SpotifyPlaylistClient playlistClient;

    @BeforeEach
    void connectAccountAndStubPlaylists() {

        accountRepository.save(new SpotifyAccount(OWNER_ID, "DJ pgoogol", "access", "refresh",
            Instant.now().plus(1, ChronoUnit.HOURS), "playlist-read-private"));
        given(playlistClient.getMyPlaylists()).willReturn(List.of(
            new SpotifyPlaylist("pl-wesela", "Wesela 2026", OWNER_ID, "DJ pgoogol", 2),
            new SpotifyPlaylist("pl-obserwowana", "Cudza salsa", "ktos-inny", "Ktoś", 1)));
        given(playlistClient.getPlaylistItems("pl-wesela")).willReturn(List.of(
            track(0, "sp-vivir", "Vivir Mi Vida", "Marc Anthony"),
            track(1, "sp-bailando", "Bailando", "Enrique Iglesias")));
    }

    @AfterEach
    void cleanDatabase() {

        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void ingestMyPlaylists_whenAccountConnected_importsOnlyOwnPlaylists() throws Exception {

        // when + then
        mockMvc.perform(post("/api/ingest/my-playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported.length()").value(1))
            .andExpect(jsonPath("$.imported[0].name").value("Wesela 2026"))
            .andExpect(jsonPath("$.imported[0].imported").value(2))
            .andExpect(jsonPath("$.failed.length()").value(0));

        assertThat(playlistRepository.findBySpotifyPlaylistId("pl-obserwowana")).isEmpty();
        assertThat(libraryEntryRepository.findByTrackSpotifyId("sp-vivir"))
            .hasValueSatisfying(entry ->
                assertThat(entry.getSource()).isEqualTo(LibrarySource.PLAYLIST));
    }

    @Test
    void ingestMyPlaylists_whenAccountNotConnected_returns400WithGuidance() throws Exception {

        // given
        accountRepository.deleteAll();

        // when + then
        mockMvc.perform(post("/api/ingest/my-playlists"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SPOTIFY_NOT_CONNECTED"));
    }

    @Test
    void ingestMyPlaylists_whenOnePlaylistFails_finishesTheRestAndReportsTheFailure()
        throws Exception {

        // given — druga własna playlista pada na pobraniu utworów
        given(playlistClient.getMyPlaylists()).willReturn(List.of(
            new SpotifyPlaylist("pl-wesela", "Wesela 2026", OWNER_ID, "DJ pgoogol", 2),
            new SpotifyPlaylist("pl-bachata", "Bachata", OWNER_ID, "DJ pgoogol", 1)));
        given(playlistClient.getPlaylistItems("pl-bachata")).willThrow(
            new ExternalServiceException("SPOTIFY_UNAVAILABLE", "Spotify nie odpowiedziało"));

        // when + then
        mockMvc.perform(post("/api/ingest/my-playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported.length()").value(1))
            .andExpect(jsonPath("$.imported[0].name").value("Wesela 2026"))
            .andExpect(jsonPath("$.failed.length()").value(1))
            .andExpect(jsonPath("$.failed[0].name").value("Bachata"))
            .andExpect(jsonPath("$.failed[0].spotifyPlaylistId").value("pl-bachata"))
            .andExpect(jsonPath("$.failed[0].errorCode").value("SPOTIFY_UNAVAILABLE"))
            .andExpect(jsonPath("$.failed[0].reason").value("Spotify nie odpowiedziało"));

        // playlista sprzed awarii została domknięta w bazie
        assertThat(playlistRepository.findBySpotifyPlaylistId("pl-wesela")).isPresent();
        assertThat(playlistRepository.findBySpotifyPlaylistId("pl-bachata")).isEmpty();
    }

    private SpotifyPlaylistItem track(int position, String spotifyId, String title, String artist) {

        return new SpotifyPlaylistItem.Track(position, new SpotifyTrackMetadata(spotifyId, title,
            artist, "Album", 2013, 252306, 80, false, null, "ISRC-" + spotifyId));
    }
}
