package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.library.LibraryEntry;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełny stos HTTP dla /api/playlists (DoD M2.3): CRUD, skład setu, kolejność
 * i slot wieczoru (D9) razem z override'em DJ-a.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaylistApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @BeforeEach
    void seedCatalog() {

        trackCatalogRepository.save(enriched("sp-peak", "Vivir Mi Vida", 186, "high",
            GenreFamily.LATIN));
        trackCatalogRepository.save(enriched("sp-warmup", "Bésame Mucho", 82, "low",
            GenreFamily.LATIN));
        trackCatalogRepository.save(new TrackCatalog("sp-nowy", "Świeżynka", "Ktoś"));
    }

    @AfterEach
    void cleanDatabase() {

        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    void createAndList_whenPlaylistCreated_showsUpWithTrackCount() throws Exception {

        // given
        long playlistId = createPlaylist("Wesele Kowalskich");

        // when + then
        mockMvc.perform(get("/api/playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(playlistId))
            .andExpect(jsonPath("$[0].name").value("Wesele Kowalskich"))
            .andExpect(jsonPath("$[0].trackCount").value(0));
    }

    @Test
    void addTrack_whenTracksAdded_appendsAtEndAndComputesSlots() throws Exception {

        // given
        long playlistId = createPlaylist("Set");

        // when
        addTrack(playlistId, "sp-warmup");
        mockMvc.perform(post("/api/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spotifyId\": \"sp-peak\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks.length()").value(2));

        // then — slot wyliczony z bpm/energy/genre_family (D9)
        mockMvc.perform(get("/api/playlists/" + playlistId))
            .andExpect(jsonPath("$.tracks[0].position").value(0))
            .andExpect(jsonPath("$.tracks[0].track.spotifyId").value("sp-warmup"))
            .andExpect(jsonPath("$.tracks[0].djSlot").value("WARMUP"))
            .andExpect(jsonPath("$.tracks[1].position").value(1))
            .andExpect(jsonPath("$.tracks[1].djSlot").value("PEAK"))
            .andExpect(jsonPath("$.tracks[1].djSlotOverride").doesNotExist());
    }

    @Test
    void addTrack_whenTrackAlreadyOnPlaylist_returns409() throws Exception {

        // given
        long playlistId = createPlaylist("Set");
        addTrack(playlistId, "sp-peak");

        // when + then
        mockMvc.perform(post("/api/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spotifyId\": \"sp-peak\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_TRACK_EXISTS"));
    }

    @Test
    void addTrack_whenTrackNotInCatalog_returns404() throws Exception {

        // given
        long playlistId = createPlaylist("Set");

        // when + then
        mockMvc.perform(post("/api/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spotifyId\": \"sp-widmo\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("TRACK_NOT_FOUND"));
    }

    @Test
    void getPlaylist_whenDjOverrodeSlot_prefersOverrideOverCalculation() throws Exception {

        // given — „Bésame Mucho" wyliczyłoby się na WARMUP, DJ chce nią zamykać
        long playlistId = createPlaylist("Set");
        addTrack(playlistId, "sp-warmup");
        libraryEntryRepository.save(new LibraryEntry(
            trackCatalogRepository.findById("sp-warmup").orElseThrow(), LibrarySource.FILE));
        mockMvc.perform(patch("/api/library/tracks/sp-warmup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"djSlotOverride\": \"closing\", \"version\": 0}"))
            .andExpect(status().isOk());

        // when + then — override zapisany kanonicznie i wygrywa z wyliczeniem
        mockMvc.perform(get("/api/playlists/" + playlistId))
            .andExpect(jsonPath("$.tracks[0].djSlot").value("CLOSING"))
            .andExpect(jsonPath("$.tracks[0].djSlotOverride").value("CLOSING"));
    }

    @Test
    void updateLibraryEntry_whenSlotOverrideUnknown_returns400() throws Exception {

        // given
        libraryEntryRepository.save(new LibraryEntry(
            trackCatalogRepository.findById("sp-peak").orElseThrow(), LibrarySource.FILE));

        // when + then
        mockMvc.perform(patch("/api/library/tracks/sp-peak")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"djSlotOverride\": \"po_polnocy\", \"version\": 0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("DJ_SLOT_INVALID"));
    }

    @Test
    void getPlaylist_whenTrackNotEnriched_leavesSlotEmpty() throws Exception {

        // given
        long playlistId = createPlaylist("Set");
        addTrack(playlistId, "sp-nowy");

        // when + then
        mockMvc.perform(get("/api/playlists/" + playlistId))
            .andExpect(jsonPath("$.tracks[0].djSlot").doesNotExist());
    }

    @Test
    void reorder_whenOrderIsPermutation_rewritesPositions() throws Exception {

        // given
        long playlistId = createPlaylist("Set");
        addTrack(playlistId, "sp-warmup");
        addTrack(playlistId, "sp-peak");

        // when + then
        mockMvc.perform(put("/api/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spotifyIds\": [\"sp-peak\", \"sp-warmup\"], \"version\": %d}"
                    .formatted(currentVersion(playlistId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[0].track.spotifyId").value("sp-peak"))
            .andExpect(jsonPath("$.tracks[0].position").value(0))
            .andExpect(jsonPath("$.tracks[1].track.spotifyId").value("sp-warmup"));
    }

    @Test
    void reorder_whenTrackMissingFromOrder_returns400AndKeepsPlaylist() throws Exception {

        // given
        long playlistId = createPlaylist("Set");
        addTrack(playlistId, "sp-warmup");
        addTrack(playlistId, "sp-peak");

        // when + then
        mockMvc.perform(put("/api/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spotifyIds\": [\"sp-peak\"], \"version\": %d}"
                    .formatted(currentVersion(playlistId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_ORDER_MISMATCH"));
        assertThat(playlistTrackRepository.countByPlaylistId(playlistId)).isEqualTo(2);
    }

    @Test
    void removeTrack_whenMiddleTrackRemoved_renumbersRemainingPositions() throws Exception {

        // given
        long playlistId = createPlaylist("Set");
        addTrack(playlistId, "sp-warmup");
        addTrack(playlistId, "sp-peak");
        addTrack(playlistId, "sp-nowy");

        // when
        mockMvc.perform(delete("/api/playlists/%d/tracks/sp-peak".formatted(playlistId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks.length()").value(2));

        // then
        mockMvc.perform(get("/api/playlists/" + playlistId))
            .andExpect(jsonPath("$.tracks[0].track.spotifyId").value("sp-warmup"))
            .andExpect(jsonPath("$.tracks[0].position").value(0))
            .andExpect(jsonPath("$.tracks[1].track.spotifyId").value("sp-nowy"))
            .andExpect(jsonPath("$.tracks[1].position").value(1));
    }

    @Test
    void renameAndDelete_whenPlaylistManaged_updatesNameThenRemovesPlaylistOnly() throws Exception {

        // given
        long playlistId = createPlaylist("Robocza");
        addTrack(playlistId, "sp-peak");

        // when + then
        mockMvc.perform(patch("/api/playlists/" + playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Wesele\", \"version\": %d}"
                    .formatted(currentVersion(playlistId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Wesele"))
            .andExpect(jsonPath("$.trackCount").value(1));

        mockMvc.perform(delete("/api/playlists/" + playlistId))
            .andExpect(status().isNoContent());

        assertThat(playlistRepository.count()).isZero();
        assertThat(playlistTrackRepository.count()).isZero();
        assertThat(trackCatalogRepository.existsById("sp-peak")).isTrue();
    }

    @Test
    void getPlaylist_whenPlaylistMissing_returns404() throws Exception {

        // when + then
        mockMvc.perform(get("/api/playlists/424242"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_NOT_FOUND"));
    }

    private long createPlaylist(String name) throws Exception {

        String body = mockMvc.perform(post("/api/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        return created.get("id").asLong();
    }

    /** Wersja agregatu (D29) rośnie z każdą zmianą składu — czytamy ją, nie zgadujemy. */
    private int currentVersion(long playlistId) throws Exception {

        String body = mockMvc.perform(get("/api/playlists/" + playlistId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("version").asInt();
    }

    private void addTrack(long playlistId, String spotifyId) throws Exception {

        mockMvc.perform(post("/api/playlists/%d/tracks".formatted(playlistId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spotifyId\": \"%s\"}".formatted(spotifyId)))
            .andExpect(status().isOk());
    }

    private TrackCatalog enriched(String spotifyId, String title, int bpm, String energy,
                                  GenreFamily genreFamily) {

        TrackCatalog track = new TrackCatalog(spotifyId, title, "Wykonawca");
        track.setBpm(bpm);
        track.setEnergy(energy);
        track.setGenreFamily(genreFamily);
        return track;
    }
}
