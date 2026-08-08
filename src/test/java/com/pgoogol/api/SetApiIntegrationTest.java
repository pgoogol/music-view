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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder propose(
            Map<String, Object> body) throws Exception {

        return post("/api/sets/propose")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    }
}
