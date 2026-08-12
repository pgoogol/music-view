package com.pgoogol.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.assertj.core.api.Assertions;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

        // dwa zapisy tej samej tonacji — na kole Camelot mają zejść się w 2A (D25)
        describe("sp-latin-1", 1998, 240_000, "Eb minor", "salsa dura", "Contra La Corriente");
        describe("sp-latin-2", 2004, 195_000, "D# minor", "bachata", "Contra La Corriente");
        describe("sp-rock-1", 2015, 300_000, "C major", "rock", "Wybór");

        addToLibrary("sp-latin-1", 5, List.of("parkiet", "pewniak"));
        addToLibrary("sp-latin-2", 3, List.of("parkiet"));
        addToLibrary("sp-rock-1", null, null);
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
            .andExpect(jsonPath("$.scale.catalogTracks").value(4))
            .andExpect(jsonPath("$.scale.libraryTracks").value(3))
            .andExpect(jsonPath("$.scale.tracksWithMetrics").value(1));
    }

    @Test
    @DisplayName("liczy czas, wykonawców i średnie tempo po bibliotece, nie po katalogu")
    void overview_reportsLibraryScale() throws Exception {

        String body = mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            // 240 + 195 + 300 tysięcy ms; utwór spoza biblioteki nie wchodzi
            .andExpect(jsonPath("$.scale.libraryDurationMs").value(735_000))
            .andExpect(jsonPath("$.scale.distinctArtists").value(1))
            // dwa utwory dzielą album, trzeci ma własny — to dwa różne wydawnictwa
            .andExpect(jsonPath("$.scale.distinctAlbums").value(2))
            .andReturn().getResponse().getContentAsString();

        JsonNode scale = new ObjectMapper().readTree(body).get("scale");

        // średnia z 184, 92 i 128 — utwór bez BPM nie zaniża wyniku
        Assertions.assertThat(scale.get("averageBpm").asDouble())
            .isCloseTo(134.67, Assertions.within(0.01));
        // średnia długość liczona po katalogu: (240 + 195 + 300) / 3 tysięcy ms
        Assertions.assertThat(scale.get("averageDurationMs").asDouble())
            .isCloseTo(245_000, Assertions.within(1.0));
    }

    @Test
    @DisplayName("udział źródeł BPM pokazuje, ile biblioteki to fakt, a ile estymata (D19)")
    void overview_reportsBpmSourceShare() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quality.bpmSources[*].label").value(hasItem("MANUAL")))
            .andExpect(jsonPath("$.quality.bpmSources[*].label").value(hasItem("LLM")))
            .andExpect(jsonPath("$.quality.bpmSources[*].label").value(hasItem("BRAK BPM")));
    }

    @Test
    @DisplayName("rozkład gatunków liczy też utwory bez gatunku")
    void overview_countsTracksWithoutGenreAsOwnBucket() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sound.genres[?(@.label == 'LATIN')].count").value(hasItem(2)))
            .andExpect(jsonPath("$.sound.genres[?(@.label == 'BEZ GATUNKU')].count").value(hasItem(1)));
    }

    @Test
    @DisplayName("histogram BPM grupuje po dziesiątkach i pomija utwory bez BPM")
    void overview_bucketsBpmByTens() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sound.bpmHistogram[*].label").value(hasItem("180–189")))
            .andExpect(jsonPath("$.sound.bpmHistogram[*].label").value(hasItem("90–99")))
            .andExpect(jsonPath("$.sound.bpmHistogram.length()").value(3));
    }

    @Test
    @DisplayName("koło Camelot skleja enharmoniczne zapisy tej samej tonacji (D25)")
    void overview_mergesEnharmonicSpellingsIntoOneWheelPosition() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            // „Eb minor" i „D# minor" to ta sama pozycja koła, więc jeden koszyk z dwoma utworami
            .andExpect(jsonPath("$.sound.camelotKeys[?(@.label == '2A')].count").value(hasItem(2)))
            .andExpect(jsonPath("$.sound.camelotKeys[?(@.label == '8B')].count").value(hasItem(1)))
            .andExpect(jsonPath("$.sound.camelotKeys[?(@.label == 'BEZ TONACJI')].count").value(hasItem(1)));
    }

    @Test
    @DisplayName("macierz tempo × energia opisuje obie osi naraz")
    void overview_crossesTempoWithEnergy() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                "$.sound.tempoEnergy[?(@.tempoClass == 'VERY_FAST' && @.energy == 'HIGH')].count")
                .value(hasItem(1)))
            .andExpect(jsonPath(
                "$.sound.tempoEnergy[?(@.tempoClass == 'BEZ TEMPA' && @.energy == 'BEZ ENERGII')].count")
                .value(hasItem(1)));
    }

    @Test
    @DisplayName("profil brzmienia uśrednia metryki ręczne (D24)")
    void overview_averagesManualMetrics() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sound.audioProfile[?(@.label == 'energy')].value")
                .value(hasItem(0.8)))
            .andExpect(jsonPath("$.sound.audioProfile[?(@.label == 'valence')].value")
                .value(hasItem(0.6)));
    }

    @Test
    @DisplayName("dekady i długości utworów wracają uporządkowane rosnąco")
    void overview_ordersDecadesAndDurations() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline.decades[0].label").value("1990s"))
            .andExpect(jsonPath("$.timeline.decades[*].label").value(hasItem("2010s")))
            .andExpect(jsonPath("$.timeline.decades[*].label").value(hasItem("BEZ ROKU")))
            .andExpect(jsonPath("$.sound.durations[0].label").value("3 min"))
            .andExpect(jsonPath("$.sound.durations[*].label").value(hasItem("BEZ CZASU")));
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

        ObjectMapper json = new ObjectMapper();
        JsonNode quality = json.readTree(overview).get("quality");
        JsonNode missingNode = json.readTree(missing);

        Assertions.assertThat(quality.get("audioMissing").asLong())
            .isEqualTo(missingNode.get("audio").asLong());
        Assertions.assertThat(quality.get("aiMissing").asLong())
            .isEqualTo(missingNode.get("ai").asLong());
    }

    @Test
    @DisplayName("najczęstsi wykonawcy liczą się po bibliotece, nie po katalogu")
    void overview_topArtistsComeFromLibrary() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taste.topArtists[0].label").value("Wykonawca"))
            .andExpect(jsonPath("$.taste.topArtists[0].count").value(3));
    }

    @Test
    @DisplayName("przegląd opisuje utwory, nie playlisty — najczęstsze albumy zamiast źródeł wpisów")
    void overview_ranksAlbumsAndKeepsPlaylistsOut() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taste.topAlbums[0].label").value("Contra La Corriente"))
            .andExpect(jsonPath("$.taste.topAlbums[0].count").value(2))
            // playlisty i sety mają własne zakładki (D36)
            .andExpect(jsonPath("$.taste.sources").doesNotExist())
            .andExpect(jsonPath("$.scale.playlists").doesNotExist())
            .andExpect(jsonPath("$.scale.tracksOutsidePlaylists").doesNotExist());
    }

    @Test
    @DisplayName("najczęstsze tagi rozwijają tablicę i pomijają wpisy bez tagów")
    void overview_topTagsUnnestCustomTags() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taste.topTags[0].label").value("parkiet"))
            .andExpect(jsonPath("$.taste.topTags[0].count").value(2))
            .andExpect(jsonPath("$.taste.topTags.length()").value(2));
    }

    @Test
    @DisplayName("przyrost biblioteki jedzie od najstarszego miesiąca")
    void overview_growthIsOldestFirst() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline.monthlyGrowth.length()").value(1))
            .andExpect(jsonPath("$.timeline.monthlyGrowth[0].count").value(3));
    }

    @Test
    @DisplayName("rozkład ocen wydziela wpisy bez oceny")
    void overview_separatesUnratedEntries() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taste.ratings[?(@.label == 'bez oceny')].count").value(hasItem(1)));
    }

    @Test
    @DisplayName("ostatnio dodane wracają od najnowszego, z okładką i wykonawcą")
    void overview_recentlyAddedIsNewestFirst() throws Exception {

        mockMvc.perform(get("/api/library/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentlyAdded.length()").value(3))
            .andExpect(jsonPath("$.recentlyAdded[0].artist").value("Wykonawca"))
            .andExpect(jsonPath("$.recentlyAdded[0].spotifyId").value("sp-rock-1"));
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

    private void describe(String spotifyId, Integer year, Integer durationMs,
                          String musicalKey, String style, String album) {

        TrackCatalog track = trackCatalogRepository.findById(spotifyId).orElseThrow();
        track.setYear(year);
        track.setDurationMs(durationMs);
        track.setMusicalKey(musicalKey);
        track.setStyle(style);
        track.setAlbum(album);
        trackCatalogRepository.save(track);
    }

    private void addToLibrary(String spotifyId, Integer rating, List<String> tags) {

        LibraryEntry entry = new LibraryEntry(
            trackCatalogRepository.findById(spotifyId).orElseThrow(), LibrarySource.PLAYLIST);
        entry.setRating(rating);
        entry.setCustomTags(tags);
        libraryEntryRepository.save(entry);
    }

    private void withMetrics(String spotifyId) {

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ManualMetrics metrics = new ManualMetrics(
                trackCatalogRepository.findById(spotifyId).orElseThrow());
            metrics.setEnergy(new BigDecimal("0.800"));
            metrics.setValence(new BigDecimal("0.600"));
            metrics.setImportedAt(Instant.now());
            manualMetricsRepository.save(metrics);
        });
    }
}
