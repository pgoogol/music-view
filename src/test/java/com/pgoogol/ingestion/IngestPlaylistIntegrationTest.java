package com.pgoogol.ingestion;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.enrichment.spotify.SpotifyPlaylist;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistClient;
import com.pgoogol.enrichment.spotify.SpotifyPlaylistItem;
import com.pgoogol.enrichment.spotify.SpotifyTrackMetadata;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import com.pgoogol.playlist.PlaylistRepository;
import com.pgoogol.playlist.PlaylistTrack;
import com.pgoogol.playlist.PlaylistTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełny stos HTTP dla POST /api/ingest/playlist na realnym Postgresie (DoD M2.1);
 * klient Spotify zamockowany — jego kontrakt pokrywa SpotifyPlaylistClientTest
 * na nagranych odpowiedziach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IngestPlaylistIntegrationTest {

    private static final String PLAYLIST_ID = "37i9dQZF1DX10zKzsJ2jva";
    private static final String IMPORT_JSON = """
        {"url": "https://open.spotify.com/playlist/37i9dQZF1DX10zKzsJ2jva?si=abc"}
        """;

    @Autowired
    private MockMvc mockMvc;

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
    void stubPlaylist() {

        given(playlistClient.getPlaylist(PLAYLIST_ID)).willReturn(
            new SpotifyPlaylist(PLAYLIST_ID, "Sabor Latino", "dj-pgoogol", "DJ pgoogol", 4));
        given(playlistClient.getPlaylistItems(PLAYLIST_ID)).willReturn(List.of(
            track(0, "sp-vivir", "Vivir Mi Vida", "Marc Anthony"),
            track(1, "sp-bailando", "Bailando", "Enrique Iglesias"),
            new SpotifyPlaylistItem.Unavailable(2, "plik lokalny — brak w katalogu Spotify"),
            track(3, "sp-vivir", "Vivir Mi Vida", "Marc Anthony")));
    }

    @AfterEach
    void cleanDatabase() {

        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    void ingestPlaylist_whenPlaylistImported_savesCatalogLibraryAndOrder() throws Exception {

        // when + then
        mockMvc.perform(post("/api/ingest/playlist")
                .contentType(MediaType.APPLICATION_JSON).content(IMPORT_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Sabor Latino"))
            .andExpect(jsonPath("$.spotifyPlaylistId").value(PLAYLIST_ID))
            .andExpect(jsonPath("$.tracks").value(2))
            .andExpect(jsonPath("$.imported").value(2))
            .andExpect(jsonPath("$.alreadyExisted").value(0))
            .andExpect(jsonPath("$.skipped.length()").value(1))
            .andExpect(jsonPath("$.skipped[0].position").value(2));

        assertThat(trackCatalogRepository.findById("sp-vivir")).hasValueSatisfying(track -> {
            assertThat(track.getTitle()).isEqualTo("Vivir Mi Vida");
            assertThat(track.getIsrc()).isEqualTo("ISRC-sp-vivir");
            assertThat(track.getYear()).isEqualTo(2013);
        });
        assertThat(libraryEntryRepository.findByTrackSpotifyId("sp-bailando"))
            .hasValueSatisfying(entry ->
                assertThat(entry.getSource()).isEqualTo(LibrarySource.FOREIGN_PLAYLIST));
        assertThat(playlistTracksInOrder())
            .containsExactly("sp-vivir", "sp-bailando");
    }

    @Test
    void ingestPlaylist_whenImportedTwiceWithNewOrder_updatesOrderWithoutDuplicates()
            throws Exception {

        // given
        mockMvc.perform(post("/api/ingest/playlist")
                .contentType(MediaType.APPLICATION_JSON).content(IMPORT_JSON))
            .andExpect(status().isOk());
        given(playlistClient.getPlaylistItems(PLAYLIST_ID)).willReturn(List.of(
            track(0, "sp-bailando", "Bailando", "Enrique Iglesias"),
            track(1, "sp-vivir", "Vivir Mi Vida", "Marc Anthony")));

        // when + then
        mockMvc.perform(post("/api/ingest/playlist")
                .contentType(MediaType.APPLICATION_JSON).content(IMPORT_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(0))
            .andExpect(jsonPath("$.alreadyExisted").value(2));

        assertThat(playlistRepository.count()).isEqualTo(1);
        assertThat(libraryEntryRepository.count()).isEqualTo(2);
        assertThat(playlistTracksInOrder()).containsExactly("sp-bailando", "sp-vivir");
    }

    @Test
    void ingestPlaylist_whenTrackAlreadyInCatalogWithoutMetadata_fillsOnlyMissingFields()
            throws Exception {

        // given — szkielet z importu CSV (M1.2): tylko tytuł i wykonawca
        TrackCatalog skeleton = new TrackCatalog("sp-vivir", "Vivir Mi Vida (CSV)", "Marc Anthony");
        trackCatalogRepository.save(skeleton);

        // when
        mockMvc.perform(post("/api/ingest/playlist")
                .contentType(MediaType.APPLICATION_JSON).content(IMPORT_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(2));

        // then — tytuł zostaje, puste pola uzupełnione z playlisty
        assertThat(trackCatalogRepository.findById("sp-vivir")).hasValueSatisfying(track -> {
            assertThat(track.getTitle()).isEqualTo("Vivir Mi Vida (CSV)");
            assertThat(track.getIsrc()).isEqualTo("ISRC-sp-vivir");
            assertThat(track.getDurationMs()).isEqualTo(252306);
        });
    }

    @Test
    void ingestPlaylist_whenUrlIsNotAPlaylist_returns400() throws Exception {

        // when + then
        mockMvc.perform(post("/api/ingest/playlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://example.com/\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_URL_INVALID"));
    }

    @Test
    void ingestPlaylist_whenUrlBlank_returns400WithValidationError() throws Exception {

        // when + then
        mockMvc.perform(post("/api/ingest/playlist")
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private List<String> playlistTracksInOrder() {

        Long playlistId = playlistRepository.findBySpotifyPlaylistId(PLAYLIST_ID).orElseThrow().getId();
        return playlistTrackRepository.findAllWithTrackByPlaylistId(playlistId).stream()
            .map(PlaylistTrack::getTrack)
            .map(TrackCatalog::getSpotifyId)
            .toList();
    }

    private SpotifyPlaylistItem track(int position, String spotifyId, String title, String artist) {

        return new SpotifyPlaylistItem.Track(position, new SpotifyTrackMetadata(spotifyId, title,
            artist, "Album", 2013, 252306, 80, false, "https://i.scdn.co/image/" + spotifyId,
            "ISRC-" + spotifyId));
    }
}
