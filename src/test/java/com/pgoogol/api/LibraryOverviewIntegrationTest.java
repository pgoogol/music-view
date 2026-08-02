package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LibraryOverviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private ManualMetricsRepository manualMetricsRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {

        save("sp-latin-1", GenreFamily.LATIN, 184, BpmSource.MANUAL, "high", TempoClass.VERY_FAST);
        save("sp-latin-2", GenreFamily.LATIN, 92, BpmSource.DEEZER, "medium", TempoClass.SLOW);
        save("sp-rock-1", GenreFamily.ROCK, 128, BpmSource.LLM, "low", TempoClass.FAST);
        save("sp-goly", null, null, null, null, null);

        addToLibrary("sp-latin-1", 5);
        addToLibrary("sp-latin-2", 3);
        addToLibrary("sp-rock-1", null);
        withMetrics("sp-latin-1");
    }

    @AfterEach
    void cleanDatabase() {

        manualMetricsRepository.deleteAll();
        libraryEntryRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    @DisplayName("podaje liczby katalogu, biblioteki i pokrycia metrykami")
    void overview_reportsHeadlineCounts() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.catalogTracks").value(4))
            .andExpect(jsonPath("$.libraryTracks").value(3))
            .andExpect(jsonPath("$.tracksWithMetrics").value(1));
    }

    @Test
    @DisplayName("udział źródeł BPM pokazuje, ile biblioteki to fakt, a ile estymata (D19)")
    void overview_reportsBpmSourceShare() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bpmSources[*].label").value(hasItem("MANUAL")))
            .andExpect(jsonPath("$.bpmSources[*].label").value(hasItem("LLM")))
            .andExpect(jsonPath("$.bpmSources[*].label").value(hasItem("BRAK BPM")));
    }

    @Test
    @DisplayName("rozkład gatunków liczy też utwory bez gatunku")
    void overview_countsTracksWithoutGenreAsOwnBucket() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.genres[?(@.label == 'LATIN')].count").value(hasItem(2)))
            .andExpect(jsonPath("$.genres[?(@.label == 'BEZ GATUNKU')].count").value(hasItem(1)));
    }

    @Test
    @DisplayName("histogram BPM grupuje po dziesiątkach i pomija utwory bez BPM")
    void overview_bucketsBpmByTens() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bpmHistogram[*].label").value(hasItem("180–189")))
            .andExpect(jsonPath("$.bpmHistogram[*].label").value(hasItem("90–99")))
            .andExpect(jsonPath("$.bpmHistogram.length()").value(3));
    }

    @Test
    @DisplayName("braki per grupa pól zgadzają się z zakładką Wzbogacanie")
    void overview_matchesMissingCountEndpoint() throws Exception {

        String overview = mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/enrich/missing-count"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper json =
            new com.fasterxml.jackson.databind.ObjectMapper();
        var overviewNode = json.readTree(overview);
        var missingNode = json.readTree(missing);

        org.assertj.core.api.Assertions.assertThat(overviewNode.get("audioMissing").asLong())
            .isEqualTo(missingNode.get("audio").asLong());
        org.assertj.core.api.Assertions.assertThat(overviewNode.get("aiMissing").asLong())
            .isEqualTo(missingNode.get("ai").asLong());
    }

    @Test
    @DisplayName("najczęstsi wykonawcy liczą się po bibliotece, nie po katalogu")
    void overview_topArtistsComeFromLibrary() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topArtists[0].label").value("Wykonawca"))
            .andExpect(jsonPath("$.topArtists[0].count").value(3));
    }

    @Test
    @DisplayName("przyrost biblioteki jedzie od najstarszego miesiąca")
    void overview_growthIsOldestFirst() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthlyGrowth.length()").value(1))
            .andExpect(jsonPath("$.monthlyGrowth[0].count").value(3));
    }

    @Test
    @DisplayName("rozkład ocen wydziela wpisy bez oceny")
    void overview_separatesUnratedEntries() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratings[?(@.label == 'bez oceny')].count").value(hasItem(1)));
    }

    private void save(String spotifyId, GenreFamily genre, Integer bpm, BpmSource source,
                      String energy, TempoClass tempoClass) {

        TrackCatalog track = new TrackCatalog(spotifyId, "Utwór " + spotifyId, "Wykonawca");
        track.setGenreFamily(genre);
        track.setBpm(bpm);
        track.setBpmSource(source);
        track.setEnergy(energy);
        track.setTempoClass(tempoClass);
        trackCatalogRepository.save(track);
    }

    private void addToLibrary(String spotifyId, Integer rating) {

        LibraryEntry entry = new LibraryEntry(
            trackCatalogRepository.findById(spotifyId).orElseThrow(), LibrarySource.PLAYLIST);
        entry.setRating(rating);
        libraryEntryRepository.save(entry);
    }

    private void withMetrics(String spotifyId) {

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ManualMetrics metrics = new ManualMetrics(
                trackCatalogRepository.findById(spotifyId).orElseThrow());
            metrics.setImportedAt(Instant.now());
            manualMetricsRepository.save(metrics);
        });
    }
}
