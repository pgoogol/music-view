package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @BeforeEach
    void seedCatalog() {

        trackCatalogRepository.save(latinTrack("sp-vivir", "Vivir Mi Vida", "Marc Anthony", 184));
        trackCatalogRepository.save(latinTrack("sp-carnaval", "La Vida Es Un Carnaval", "Celia Cruz", 92));
        TrackCatalog rock = new TrackCatalog("sp-bohemian", "Bohemian Rhapsody", "Queen");
        rock.setGenreFamily(GenreFamily.ROCK);
        rock.setBpm(72);
        rock.setTempoClass(TempoClass.SLOW);
        rock.setEnergy("medium");
        trackCatalogRepository.save(rock);
    }

    @AfterEach
    void cleanDatabase() {
        trackCatalogRepository.deleteAll();
    }

    @Test
    void getTrack_whenTrackExists_returnsFullRecord() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks/sp-vivir"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Vivir Mi Vida"))
            .andExpect(jsonPath("$.genreFamily").value("LATIN"))
            .andExpect(jsonPath("$.bpm").value(184))
            .andExpect(jsonPath("$.bpmSource").value("DEEZER"))
            .andExpect(jsonPath("$.tempoClass").value("VERY_FAST"));
    }

    @Test
    void getTrack_whenTrackMissing_returns404WithErrorCode() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks/sp-nieistnieje"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("TRACK_NOT_FOUND"));
    }

    @Test
    void searchTracks_whenFullTextQuery_returnsMatchingTracksOnly() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks").param("search", "vida"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].spotifyId").value(
                org.hamcrest.Matchers.containsInAnyOrder("sp-vivir", "sp-carnaval")));
    }

    @Test
    void searchTracks_whenGenreAndBpmRangeFilters_combinesThem() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks")
                .param("genreFamily", "LATIN")
                .param("bpmMin", "100")
                .param("bpmMax", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenTempoClassAndEnergyFilters_matchRockTrack() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks")
                .param("tempoClass", "SLOW")
                .param("energy", "MEDIUM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-bohemian"));
    }

    @Test
    void searchTracks_whenPageSizeAboveLimit_capsAtHundred() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks").param("size", "5000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    private TrackCatalog latinTrack(String spotifyId, String title, String artist, int bpm) {

        TrackCatalog track = new TrackCatalog(spotifyId, title, artist);
        track.setGenreFamily(GenreFamily.LATIN);
        track.setBpm(bpm);
        track.setBpmSource(BpmSource.DEEZER);
        track.setTempoClass(bpm > 160 ? TempoClass.VERY_FAST : TempoClass.SLOW);
        track.setEnergy("high");
        return track;
    }
}
