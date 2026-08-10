package com.pgoogol.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import com.pgoogol.playlist.PlaylistRepository;
import com.pgoogol.playlist.PlaylistTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SetApiIntegrationTest {

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

        IntStream.range(0, 60).forEach(index -> {
            TrackCatalog track = new TrackCatalog(
                "sp-%02d".formatted(index), "Utwór %d".formatted(index),
                "Wykonawca %d".formatted(index % 10));
            track.setGenreFamily(index % 5 == 0 ? GenreFamily.ROCK : GenreFamily.LATIN);
            track.setBpm(90 + (index % 8) * 8);
            track.setDurationMs(210_000);
            track.setTempoClass(TempoClass.MEDIUM);
            track.setEnergy(switch (index % 3) {
                case 0 -> "low";
                case 1 -> "medium";
                default -> "high";
            });
            trackCatalogRepository.save(track);
        });
    }

    @AfterEach
    void cleanDatabase() {

        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    @DisplayName("układa set na zadany czas i nie zapisuje żadnej playlisty (D26)")
    void propose_returnsSetWithoutPersistingAnything() throws Exception {

        mockMvc.perform(propose(Map.of("targetMinutes", 60, "seed", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackCount").value(org.hamcrest.Matchers.greaterThan(10)))
            .andExpect(jsonPath("$.targetDurationMs").value(3_600_000))
            .andExpect(jsonPath("$.seed").value(1))
            .andExpect(jsonPath("$.tracks[0].djSlot").value("WARMUP"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/playlists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("ten sam seed daje tę samą propozycję")
    void propose_isReproducibleForTheSameSeed() throws Exception {

        String first = mockMvc.perform(propose(Map.of("targetMinutes", 45, "seed", 99)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(propose(Map.of("targetMinutes", 45, "seed", 99)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("filtry puli działają tak samo jak w wyszukiwarce")
    void propose_appliesCatalogFilters() throws Exception {

        mockMvc.perform(propose(Map.of("targetMinutes", 30, "seed", 4, "genreFamily", "ROCK")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks[*].track.genreFamily")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ROCK"))));
    }

    @Test
    @DisplayName("gdy filtry nie zostawiają kandydatów, mówi to wprost")
    void propose_whenNoCandidates_returns400WithErrorCode() throws Exception {

        mockMvc.perform(propose(Map.of("targetMinutes", 60, "bpmMin", 400)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SET_NO_CANDIDATES"));
    }

    @Test
    @DisplayName("za krótki wieczór odbija się o walidację żądania")
    void propose_whenTargetTooShort_returns400() throws Exception {

        mockMvc.perform(propose(Map.of("targetMinutes", 5)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("uboga pula kończy się krótszym setem z notatką, nie błędem")
    void propose_whenPoolTooSmall_returnsShorterSetWithNote() throws Exception {

        libraryEntryRepository.save(new LibraryEntry(
            trackCatalogRepository.findById("sp-01").orElseThrow(), LibrarySource.PLAYLIST));

        mockMvc.perform(propose(Map.of("targetMinutes", 240, "seed", 2, "inLibrary", true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackCount").value(1))
            .andExpect(jsonPath("$.notes").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    @DisplayName("nieprawidłowa tonacja to 400, nie 500")
    void propose_whenCamelotInvalid_returns400WithErrorCode() throws Exception {

        mockMvc.perform(propose(Map.of("targetMinutes", 60, "camelot", "99Z")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CAMELOT"));
    }

    @Test
    @DisplayName("uzupełnia gotowy set i nie dopisuje niczego do playlisty (D32)")
    void fill_extendsSetWithoutPersistingAnything() throws Exception {

        long playlistId = createSet("Wesele", "sp-00", "sp-01");

        mockMvc.perform(fill(playlistId, Map.of("targetMinutes", 60, "seed", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTrackCount").value(2))
            .andExpect(jsonPath("$.currentDurationMs").value(420_000))
            .andExpect(jsonPath("$.addedTrackCount")
                .value(org.hamcrest.Matchers.greaterThan(5)))
            .andExpect(jsonPath("$.targetDurationMs").value(3_600_000))
            // dalszy ciąg zaczyna się tam, gdzie kończy się set
            .andExpect(jsonPath("$.tracks[0].position").value(2))
            .andExpect(jsonPath("$.tracks[*].track.spotifyId")
                .value(org.hamcrest.Matchers.everyItem(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.oneOf("sp-00", "sp-01")))));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/playlists/{id}", playlistId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tracks.length()").value(2));
    }

    @Test
    @DisplayName("ten sam seed daje ten sam dalszy ciąg")
    void fill_isReproducibleForTheSameSeed() throws Exception {

        long playlistId = createSet("Wesele", "sp-00");

        String first = mockMvc.perform(fill(playlistId, Map.of("targetMinutes", 45, "seed", 7)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(fill(playlistId, Map.of("targetMinutes", 45, "seed", 7)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("set dłuższy od zamówionego czasu kończy się notatką, nie błędem")
    void fill_whenSetAlreadyLongEnough_returnsNoteInsteadOfError() throws Exception {

        long playlistId = createSet("Długi", "sp-00", "sp-01", "sp-02", "sp-03", "sp-04");

        mockMvc.perform(fill(playlistId, Map.of("targetMinutes", 15, "seed", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.addedTrackCount").value(0))
            .andExpect(jsonPath("$.notes").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.empty())));
    }

    @Test
    @DisplayName("uzupełnianie nieistniejącego setu to 404")
    void fill_whenPlaylistMissing_returns404() throws Exception {

        mockMvc.perform(fill(9_999L, Map.of("targetMinutes", 60)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("PLAYLIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("dobiera kandydatów na koniec setu z powodami przejścia")
    void suggest_returnsRankedCandidatesForTheEndOfTheSet() throws Exception {

        long playlistId = createSet("Wesele", "sp-00");

        mockMvc.perform(suggest(playlistId, Map.of("limit", 3)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.position").value(1))
            .andExpect(jsonPath("$.suggestions.length()").value(3))
            .andExpect(jsonPath("$.suggestions[0].track.spotifyId")
                .value(org.hamcrest.Matchers.not("sp-00")))
            .andExpect(jsonPath("$.suggestions[0].bpmDelta")
                .value(org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    @DisplayName("dobieranie nie losuje — to samo pytanie daje tę samą odpowiedź")
    void suggest_isDeterministic() throws Exception {

        long playlistId = createSet("Wesele", "sp-00");

        String first = mockMvc.perform(suggest(playlistId, Map.of()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(suggest(playlistId, Map.of()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("pozycja poza setem to 400 z kodem, nie pusta lista")
    void suggest_whenPositionOutOfRange_returns400WithErrorCode() throws Exception {

        long playlistId = createSet("Wesele", "sp-00");

        mockMvc.perform(suggest(playlistId, Map.of("position", 5)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("SET_POSITION_OUT_OF_RANGE"));
    }

    private long createSet(String name, String... spotifyIds) throws Exception {

        String created = mockMvc.perform(post("/api/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long playlistId = objectMapper.readTree(created).get("id").asLong();
        for (String spotifyId : spotifyIds) {
            mockMvc.perform(post("/api/playlists/{id}/tracks", playlistId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("spotifyId", spotifyId))))
                .andExpect(status().isOk());
        }
        return playlistId;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder propose(
            Map<String, Object> body) throws Exception {

        return post("/api/sets/propose")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder fill(
            long playlistId, Map<String, Object> body) throws Exception {

        return post("/api/sets/{id}/fill", playlistId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder suggest(
            long playlistId, Map<String, Object> body) throws Exception {

        return post("/api/sets/{id}/suggest", playlistId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    }
}
