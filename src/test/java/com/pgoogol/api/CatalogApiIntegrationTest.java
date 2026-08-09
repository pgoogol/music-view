package com.pgoogol.api;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.LyricsStatus;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.catalog.TrackLyrics;
import com.pgoogol.catalog.TrackLyricsRepository;
import com.pgoogol.library.LibraryEntry;
import com.pgoogol.library.LibraryEntryRepository;
import com.pgoogol.library.LibrarySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private LibraryEntryRepository libraryEntryRepository;

    @Autowired
    private ManualMetricsRepository manualMetricsRepository;

    @Autowired
    private TrackLyricsRepository trackLyricsRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seedCatalog() {

        trackCatalogRepository.save(latinTrack("sp-vivir", "Vivir Mi Vida", "Marc Anthony", 184));
        trackCatalogRepository.save(latinTrack("sp-carnaval", "La Vida Es Un Carnaval", "Celia Cruz", 92));
        TrackCatalog rock = new TrackCatalog("sp-bohemian", "Bohemian Rhapsody", "Queen");
        rock.setGenreFamily(GenreFamily.ROCK);
        rock.setBpm(72);
        rock.setTempoClass(TempoClass.SLOW);
        rock.setEnergy("medium");
        // 8B — tonacja równoległa do 8A utworu sp-vivir (D25)
        rock.setMusicalKey("C major");
        trackCatalogRepository.save(rock);
    }

    @AfterEach
    void cleanDatabase() {

        manualMetricsRepository.deleteAll();
        trackLyricsRepository.deleteAll();
        libraryEntryRepository.deleteAll();
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
    void getTrackLyrics_whenTranslationExists_returnsTextWithAudit() throws Exception {

        // given — tekst wymyślony na potrzeby testu (D32)
        TrackLyrics lyrics = new TrackLyrics("sp-vivir", LyricsStatus.TRANSLATED);
        lyrics.setSourceLanguage("hiszpański");
        lyrics.setOriginalLyrics("Pierwszy wers testowego tekstu");
        lyrics.setTranslationPl("Tłumaczenie testowe");
        lyrics.setInterpretationPl("Interpretacja testowa.");
        lyrics.setModelUsed("test-model");
        lyrics.setPromptVersion(1);
        trackLyricsRepository.save(lyrics);

        // when + then
        mockMvc.perform(get("/api/catalog/tracks/sp-vivir/lyrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRANSLATED"))
            .andExpect(jsonPath("$.sourceLanguage").value("hiszpański"))
            .andExpect(jsonPath("$.translationPl").value("Tłumaczenie testowe"))
            .andExpect(jsonPath("$.interpretationPl").value("Interpretacja testowa."))
            .andExpect(jsonPath("$.modelUsed").value("test-model"));
    }

    @Test
    void getTrackLyrics_whenTrackHasNoLyricsYet_returns204() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks/sp-carnaval/lyrics"))
            .andExpect(status().isNoContent());
    }

    @Test
    void getTrackLyrics_whenLrcLibKnowsNothing_returnsAnswerNotEmptyBody() throws Exception {

        // given — potwierdzony brak to odpowiedź, nie luka: front ma co pokazać
        trackLyricsRepository.save(new TrackLyrics("sp-bohemian", LyricsStatus.NOT_FOUND));

        // when + then
        mockMvc.perform(get("/api/catalog/tracks/sp-bohemian/lyrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NOT_FOUND"))
            .andExpect(jsonPath("$.translationPl").doesNotExist());
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

    @Test
    void searchTracks_whenSortedByBpmDescending_ordersAcrossWholeResult() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks")
                .param("sort", "BPM")
                .param("direction", "DESC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"))
            .andExpect(jsonPath("$.content[1].spotifyId").value("sp-carnaval"))
            .andExpect(jsonPath("$.content[2].spotifyId").value("sp-bohemian"));
    }

    @Test
    void searchTracks_whenSortedByTitleAscending_ordersAlphabetically() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks")
                .param("sort", "TITLE")
                .param("direction", "ASC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].title").value("Bohemian Rhapsody"))
            .andExpect(jsonPath("$.content[1].title").value("La Vida Es Un Carnaval"))
            .andExpect(jsonPath("$.content[2].title").value("Vivir Mi Vida"));
    }

    @Test
    void searchTracks_whenSortedByFieldWithoutValue_keepsMissingDataLast() throws Exception {

        trackCatalogRepository.save(new TrackCatalog("sp-szkielet", "Szkielet", "Nieznany"));

        mockMvc.perform(get("/api/catalog/tracks")
                .param("sort", "BPM")
                .param("direction", "ASC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(4))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-bohemian"))
            .andExpect(jsonPath("$.content[3].spotifyId").value("sp-szkielet"));
    }

    @Test
    void searchTracks_whenSortedByEnergy_ordersLowMediumHigh() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks")
                .param("sort", "ENERGY")
                .param("direction", "ASC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-bohemian"))
            .andExpect(jsonPath("$.content[1].energy").value("high"))
            .andExpect(jsonPath("$.content[2].energy").value("high"));
    }

    @Test
    void searchTracks_whenSortIsNotOnWhitelist_returns400WithErrorCode() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks").param("sort", "DROP TABLE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void searchTracks_whenNoSortRequested_keepsRelevanceOrderFromM17() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks").param("search", "carnaval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-carnaval"));
    }

    @Test
    void searchTracks_whenInLibraryFilter_returnsOnlyTracksFromLibrary() throws Exception {

        addToLibrary("sp-vivir", 5, "wesele");

        mockMvc.perform(get("/api/catalog/tracks").param("inLibrary", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenInLibraryFalse_returnsOnlyTracksOutsideLibrary() throws Exception {

        addToLibrary("sp-vivir", 5, "wesele");

        mockMvc.perform(get("/api/catalog/tracks").param("inLibrary", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].spotifyId").value(
                org.hamcrest.Matchers.containsInAnyOrder("sp-carnaval", "sp-bohemian")));
    }

    @Test
    void searchTracks_whenRatingMinFilter_dropsWeakerAndUnratedTracks() throws Exception {

        addToLibrary("sp-vivir", 5, "wesele");
        addToLibrary("sp-carnaval", 2, "wesele");
        addToLibrary("sp-bohemian", null, null);

        mockMvc.perform(get("/api/catalog/tracks").param("ratingMin", "4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenTagFilter_matchesCustomTagOfLibraryEntry() throws Exception {

        addToLibrary("sp-vivir", 5, "wesele");
        addToLibrary("sp-carnaval", 3, "chill");

        mockMvc.perform(get("/api/catalog/tracks").param("tag", "chill"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-carnaval"));
    }

    @Test
    void searchTracks_whenLibraryFilterCombinedWithCatalogFilter_appliesBoth() throws Exception {

        addToLibrary("sp-vivir", 5, "wesele");
        addToLibrary("sp-bohemian", 5, "wesele");

        mockMvc.perform(get("/api/catalog/tracks")
                .param("inLibrary", "true")
                .param("genreFamily", "LATIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenLibraryFilterActive_leavesCatalogSortingUntouched() throws Exception {

        addToLibrary("sp-vivir", 5, null);
        addToLibrary("sp-carnaval", 3, null);

        mockMvc.perform(get("/api/catalog/tracks")
                .param("inLibrary", "true")
                .param("sort", "BPM")
                .param("direction", "DESC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"))
            .andExpect(jsonPath("$.content[1].spotifyId").value("sp-carnaval"));
    }

    @Test
    void getTrack_computesCamelotFromMusicalKey() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks/sp-vivir"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.musicalKey").value("A minor"))
            .andExpect(jsonPath("$.camelot").value("8A"));
    }

    @Test
    void getTrack_whenKeyUnknown_leavesCamelotEmpty() throws Exception {

        trackCatalogRepository.save(new TrackCatalog("sp-szkielet", "Szkielet", "Nieznany"));

        mockMvc.perform(get("/api/catalog/tracks/sp-szkielet"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.camelot").doesNotExist());
    }

    @Test
    void searchTracks_whenCamelotCompatible_returnsNeighboursAndRelativeKey() throws Exception {

        saveTrackWithKey("sp-daleki", "Daleki", "Eb minor");

        mockMvc.perform(get("/api/catalog/tracks").param("camelot", "8A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[*].spotifyId").value(
                org.hamcrest.Matchers.containsInAnyOrder("sp-vivir", "sp-carnaval", "sp-bohemian")));
    }

    @Test
    void searchTracks_whenCamelotExact_returnsOnlyThatPosition() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks")
                .param("camelot", "8A")
                .param("camelotCompatible", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenCamelotWrittenWithFlats_matchesSharpSpellingInCatalog() throws Exception {

        saveTrackWithKey("sp-bemol", "Bemol", "Eb minor");

        mockMvc.perform(get("/api/catalog/tracks")
                .param("camelot", "2A")
                .param("camelotCompatible", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-bemol"))
            .andExpect(jsonPath("$.content[0].camelot").value("2A"));
    }

    @Test
    void searchTracks_whenCamelotOutsideWheel_returns400WithErrorCode() throws Exception {

        mockMvc.perform(get("/api/catalog/tracks").param("camelot", "13Z"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CAMELOT"));
    }

    @Test
    void searchTracks_whenMetricFilter_keepsOnlyTracksWithMatchingMetrics() throws Exception {

        saveMetrics("sp-vivir", "0.80", "0.05", "0.10");
        saveMetrics("sp-carnaval", "0.20", "0.90", "0.10");

        mockMvc.perform(get("/api/catalog/tracks").param("valenceMin", "0.5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenMetricFilter_dropsTracksWithoutMetricsAltogether() throws Exception {

        saveMetrics("sp-vivir", "0.80", "0.95", "0.10");

        mockMvc.perform(get("/api/catalog/tracks").param("instrumentalMin", "0.5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-vivir"));
    }

    @Test
    void searchTracks_whenLivenessMax_filtersOutConcertRecordings() throws Exception {

        saveMetrics("sp-vivir", "0.80", "0.05", "0.90");
        saveMetrics("sp-carnaval", "0.80", "0.05", "0.10");

        mockMvc.perform(get("/api/catalog/tracks").param("livenessMax", "0.5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].spotifyId").value("sp-carnaval"));
    }

    @Test
    void getMetricsCoverage_reportsHowManyTracksHaveMetrics() throws Exception {

        saveMetrics("sp-vivir", "0.80", "0.05", "0.10");

        mockMvc.perform(get("/api/catalog/metrics-coverage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.withMetrics").value(1))
            .andExpect(jsonPath("$.total").value(3));
    }

    private void saveTrackWithKey(String spotifyId, String title, String musicalKey) {

        TrackCatalog track = new TrackCatalog(spotifyId, title, "Nieznany");
        track.setMusicalKey(musicalKey);
        trackCatalogRepository.save(track);
    }

    /**
     * {@code ManualMetrics} dzieli klucz z utworem ({@code @MapsId}), więc utwór
     * musi być encją zarządzaną — inaczej Hibernate uzna go za nowy i spróbuje
     * wstawić drugi raz. Produkcyjny import robi to samo wewnątrz transakcji.
     */
    private void saveMetrics(String spotifyId, String valence, String instrumentalness,
                             String liveness) {

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ManualMetrics metrics = new ManualMetrics(
                trackCatalogRepository.findById(spotifyId).orElseThrow());
            metrics.setValence(new BigDecimal(valence));
            metrics.setInstrumentalness(new BigDecimal(instrumentalness));
            metrics.setLiveness(new BigDecimal(liveness));
            metrics.setImportedAt(Instant.now());
            manualMetricsRepository.save(metrics);
        });
    }

    private void addToLibrary(String spotifyId, Integer rating, String tag) {

        LibraryEntry entry = new LibraryEntry(
            trackCatalogRepository.findById(spotifyId).orElseThrow(), LibrarySource.FILE);
        entry.setRating(rating);
        if (tag != null) {
            entry.setCustomTags(List.of(tag));
        }
        libraryEntryRepository.save(entry);
    }

    private TrackCatalog latinTrack(String spotifyId, String title, String artist, int bpm) {

        TrackCatalog track = new TrackCatalog(spotifyId, title, artist);
        track.setGenreFamily(GenreFamily.LATIN);
        track.setBpm(bpm);
        track.setBpmSource(BpmSource.DEEZER);
        track.setTempoClass(bpm > 160 ? TempoClass.VERY_FAST : TempoClass.SLOW);
        track.setEnergy("high");
        // 8A i 9A — sąsiedzi na kole Camelot, żeby filtr harmoniczny miał co dopasować
        track.setMusicalKey("sp-vivir".equals(spotifyId) ? "A minor" : "E minor");
        return track;
    }
}
