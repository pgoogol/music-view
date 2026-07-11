package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.library.LibraryEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LibraryApiIntegrationTest {

    private static final String ADD_VIVIR_JSON = """
        {"spotifyId": "sp-vivir", "title": "Vivir Mi Vida", "artist": "Marc Anthony", "album": "3.0"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @AfterEach
    void cleanDatabase() {

        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    void addTrack_whenNewTrack_createsCatalogSkeletonAndEntry() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.spotifyId").value("sp-vivir"))
            .andExpect(jsonPath("$.source").value("FILE"))
            .andExpect(jsonPath("$.track.title").value("Vivir Mi Vida"))
            .andExpect(jsonPath("$.track.album").value("3.0"));

        assertThat(trackCatalogRepository.existsById("sp-vivir")).isTrue();
        assertThat(libraryEntryRepository.existsByTrackSpotifyId("sp-vivir")).isTrue();
    }

    @Test
    void addTrack_whenAlreadyInLibrary_returns409() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("LIBRARY_ENTRY_EXISTS"));
    }

    @Test
    void addTrack_whenBlankTitle_returns400WithValidationError() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spotifyId\": \"sp-x\", \"title\": \"\", \"artist\": \"Ktoś\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void getTrack_whenEntryExists_returnsEntryJoinedWithCatalog() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/library/tracks/sp-vivir"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spotifyId").value("sp-vivir"))
            .andExpect(jsonPath("$.track.title").value("Vivir Mi Vida"));

        mockMvc.perform(get("/api/library/tracks/sp-nieistnieje"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("LIBRARY_ENTRY_NOT_FOUND"));
    }

    @Test
    void listTracks_whenEntryExists_returnsEntryJoinedWithCatalog() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/library/tracks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"))
            .andExpect(jsonPath("$.content[0].track.artist").value("Marc Anthony"));
    }

    @Test
    void updateTrack_whenPrivateFieldsPatched_updatesAndClearsSelectively() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated());

        // ustawienie pól prywatnych
        mockMvc.perform(patch("/api/library/tracks/sp-vivir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"djNotes": "pewniak na parkiet", "customTags": ["wesele", "opener"], "rating": 5}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.djNotes").value("pewniak na parkiet"))
            .andExpect(jsonPath("$.customTags[0]").value("wesele"))
            .andExpect(jsonPath("$.rating").value(5));

        // czyszczenie notatki pustym stringiem; rating i tagi bez zmian (null)
        mockMvc.perform(patch("/api/library/tracks/sp-vivir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"djNotes\": \"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.djNotes").doesNotExist())
            .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void updateTrack_whenRatingOutOfRange_returns400() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/library/tracks/sp-vivir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 7}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("RATING_OUT_OF_RANGE"));
    }

    @Test
    void updateTrack_whenEntryMissing_returns404() throws Exception {

        mockMvc.perform(patch("/api/library/tracks/sp-nieistnieje")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 3}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("LIBRARY_ENTRY_NOT_FOUND"));
    }

    @Test
    void deleteTrack_whenEntryExists_removesEntryButKeepsCatalogRecord() throws Exception {

        mockMvc.perform(post("/api/library/tracks")
                .contentType(MediaType.APPLICATION_JSON).content(ADD_VIVIR_JSON))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/library/tracks/sp-vivir"))
            .andExpect(status().isNoContent());

        assertThat(libraryEntryRepository.count()).isZero();
        assertThat(trackCatalogRepository.existsById("sp-vivir")).isTrue();

        mockMvc.perform(delete("/api/library/tracks/sp-vivir"))
            .andExpect(status().isNotFound());
    }
}
