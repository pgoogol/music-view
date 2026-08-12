package com.pgoogol.enrichment;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.enrichment.deezer.DeezerClient;
import com.pgoogol.enrichment.llm.TrackAnalysis;
import com.pgoogol.enrichment.llm.TrackAnalysisResult;
import com.pgoogol.enrichment.llm.TrackAnalysisService;
import com.pgoogol.enrichment.musicbrainz.MusicBrainzClient;
import com.pgoogol.enrichment.spotify.SpotifyClient;
import com.pgoogol.enrichment.spotify.SpotifyTrackMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Testy integracyjne joba wzbogacania (DoD M1.6) na realnym Postgresie:
 * pełny przebieg, częściowy postęp po awarii i restart od checkpointu
 * bez ponownego przetwarzania (klienci źródeł i LLM zamockowani —
 * przetestowani osobno na WireMocku).
 */
@SpringBootTest(properties = "llm.model=test-model")
@Import(TestcontainersConfiguration.class)
class EnrichmentJobIntegrationTest {

    @Autowired
    private EnrichmentService enrichmentService;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @Autowired
    private ManualMetricsRepository manualMetricsRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private SpotifyClient spotifyClient;

    @MockitoBean
    private MusicBrainzClient musicBrainzClient;

    @MockitoBean
    private DeezerClient deezerClient;

    @MockitoBean
    private TrackAnalysisService trackAnalysisService;

    private final List<List<String>> analyzedBatches = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failOnSecondChunk = new AtomicBoolean(false);
    /** Utwór, który pada przy każdej próbie — do sprawdzenia pomijania (D37). */
    private final AtomicReference<String> alwaysFailingTrack = new AtomicReference<>();

    @BeforeEach
    void stubSources() {

        analyzedBatches.clear();
        failOnSecondChunk.set(false);
        alwaysFailingTrack.set(null);
        given(spotifyClient.getTracks(any())).willAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return ids.stream().map(this::spotifyMetadata).toList();
        });
        given(musicBrainzClient.lookupMbid(anyString())).willReturn(Optional.empty());
        given(deezerClient.findBpmByIsrc(anyString())).willReturn(Optional.of(new BigDecimal("120")));
        given(trackAnalysisService.analyze(anyList(), anyBoolean())).willAnswer(invocation -> {
            List<TrackCatalog> batch = invocation.getArgument(0);
            List<String> batchIds = batch.stream().map(TrackCatalog::getSpotifyId).toList();
            if (failOnSecondChunk.get() && batchIds.contains("trk-005")) {
                failOnSecondChunk.set(false);
                throw new com.pgoogol.common.ExternalServiceException(
                    "LLM_UNAVAILABLE", "symulowana awaria w trakcie joba");
            }
            String failing = alwaysFailingTrack.get();
            if (Objects.nonNull(failing) && batchIds.contains(failing)) {
                throw new com.pgoogol.common.ExternalServiceException(
                    "LLM_UNAVAILABLE", "symulowana trwała awaria dla " + failing);
            }
            analyzedBatches.add(batchIds);
            List<TrackAnalysis> analyses = batch.stream()
                .map(track -> new TrackAnalysis(track.getSpotifyId(), "salsa", GenreFamily.LATIN,
                    "temat", "Opis PL.", "high", "high", null))
                .toList();
            return new TrackAnalysisResult(analyses, 100, 80);
        });
    }

    @AfterEach
    void cleanDatabase() {

        manualMetricsRepository.deleteAll();
        trackCatalogRepository.deleteAll();
    }

    @Test
    void start_whenMissingScopeWithAllGroups_enrichesAllTracksIncrementally() {

        // given
        seedSkeletons(12);

        // when
        long executionId = enrichmentService.start(
            EnrichmentScope.MISSING, EnumSet.allOf(FieldGroup.class), List.of());
        awaitStatus(executionId, "COMPLETED");

        // then
        EnrichmentJobStatus status = enrichmentService.status(executionId);
        assertThat(status.readCount()).isEqualTo(12);
        assertThat(status.writeCount()).isEqualTo(12);
        List<TrackCatalog> enriched = trackCatalogRepository.findAll();
        assertThat(enriched).hasSize(12);
        assertThat(enriched).allSatisfy(track -> {
            assertThat(track.getIsrc()).startsWith("ISRC-");
            assertThat(track.getYear()).isEqualTo(2020);
            assertThat(track.getBpm()).isEqualTo(120);
            assertThat(track.getBpmSource()).isEqualTo(BpmSource.DEEZER);
            assertThat(track.getTempoClass()).isEqualTo(TempoClass.MEDIUM);
            assertThat(track.getGenreFamily()).isEqualTo(GenreFamily.LATIN);
            assertThat(track.getDescriptionPl()).isEqualTo("Opis PL.");
            assertThat(track.getEnrichedAt()).isNotNull();
            assertThat(track.getModelUsed()).isEqualTo("test-model");
            assertThat(track.getEnrichVersion()).isEqualTo(1);
        });
        assertThat(enrichmentService.missingCount().ai()).isZero();
    }

    @Test
    @DisplayName("trwała awaria jednego utworu nie przerywa przebiegu — reszta wchodzi (D37)")
    void start_whenOneTrackKeepsFailing_skipsItAndFinishesTheRest() {

        // given — trk-005 pada przy każdej próbie, także po powtórce chunka
        seedSkeletons(10);
        alwaysFailingTrack.set("trk-005");

        // when
        long executionId = enrichmentService.start(
            EnrichmentScope.MISSING, EnumSet.allOf(FieldGroup.class), List.of());

        // then — job kończy się sukcesem mimo porażki, bo porażka jednego utworu
        // nie jest porażką przebiegu; wcześniej wywracała cały job
        awaitStatus(executionId, "COMPLETED");

        assertThat(trackCatalogRepository.findAll().stream()
            .filter(track -> Objects.nonNull(track.getGenreFamily())))
            .extracting(TrackCatalog::getSpotifyId)
            .hasSize(9)
            .doesNotContain("trk-005");

        EnrichmentJobStatus status = enrichmentService.status(executionId);
        assertThat(status.failedCount()).isEqualTo(1);

        // i mówi, co konkretnie padło — sam licznik nie wystarcza do decyzji
        assertThat(enrichmentService.failures(executionId, 50))
            .singleElement()
            .satisfies(failure -> {
                assertThat(failure.spotifyId()).isEqualTo("trk-005");
                assertThat(failure.reason()).contains("symulowana trwała awaria");
            });
    }

    @Test
    void start_whenLatinTrackWithHalfTimeBpm_appliesCorrectionAfterAiPhase() {

        // given — Deezer daje 92.5 (half-time salsy); gatunek latin znany dopiero
        // po fazie AI, więc korekta musi zadziałać na końcu chunka
        seedSkeletons(1);
        given(deezerClient.findBpmByIsrc(anyString())).willReturn(Optional.of(new BigDecimal("92.5")));

        // when
        long executionId = enrichmentService.start(
            EnrichmentScope.MISSING, EnumSet.allOf(FieldGroup.class), List.of());
        awaitStatus(executionId, "COMPLETED");

        // then — 93 → 186 (latin, <100) i tempo_class z wartości po korekcie
        assertThat(trackCatalogRepository.findById("trk-000")).hasValueSatisfying(track -> {
            assertThat(track.getBpm()).isEqualTo(186);
            assertThat(track.getBpmSource()).isEqualTo(BpmSource.DEEZER);
            assertThat(track.getTempoClass()).isEqualTo(TempoClass.VERY_FAST);
        });
    }

    @Test
    void start_whenTrackHasMetricsFromFile_keepsThemInsteadOfLlmEstimate() {

        // given — plik mówi rock i energię 0.15, LLM w stubie twierdzi latin i „high" (D34).
        // Metryki zapisujemy w jednej transakcji z odczytem utworu, tak jak robi to
        // import (D24): klucz jest dzielony przez @MapsId, więc utwór musi być
        // w tym momencie zarządzany, inaczej Hibernate próbuje wstawić go drugi raz.
        seedSkeletons(1);
        transactionTemplate.executeWithoutResult(status -> {
            TrackCatalog track = trackCatalogRepository.findById("trk-000").orElseThrow();
            ManualMetrics metrics = new ManualMetrics(track);
            metrics.setGenreFamily(GenreFamily.ROCK);
            metrics.setEnergy(new BigDecimal("0.15"));
            metrics.setImportedAt(Instant.now());
            manualMetricsRepository.save(metrics);
        });

        // when
        long executionId = enrichmentService.start(
            EnrichmentScope.MISSING, EnumSet.allOf(FieldGroup.class), List.of());
        awaitStatus(executionId, "COMPLETED");

        // then — zmierzone zostaje, reszta analizy jest AI-owa
        assertThat(trackCatalogRepository.findById("trk-000")).hasValueSatisfying(enriched -> {
            assertThat(enriched.getGenreFamily()).isEqualTo(GenreFamily.ROCK);
            assertThat(enriched.getEnergy()).isEqualTo("low");
            assertThat(enriched.getDescriptionPl()).isEqualTo("Opis PL.");
            assertThat(enriched.getStyle()).isEqualTo("salsa");
        });
    }

    @Test
    void start_whenSelectedScope_enrichesOnlyChosenTracks() {

        // given
        seedSkeletons(4);

        // when
        long executionId = enrichmentService.start(EnrichmentScope.SELECTED,
            EnumSet.of(FieldGroup.METADATA), List.of("trk-001", "trk-003"));
        awaitStatus(executionId, "COMPLETED");

        // then
        assertThat(trackCatalogRepository.findById("trk-001"))
            .hasValueSatisfying(track -> assertThat(track.getIsrc()).isEqualTo("ISRC-trk-001"));
        assertThat(trackCatalogRepository.findById("trk-000"))
            .hasValueSatisfying(track -> assertThat(track.getIsrc()).isNull());
        assertThat(enrichmentService.missingCount().metadata()).isEqualTo(2);
    }

    private void seedSkeletons(int count) {

        List<TrackCatalog> tracks = IntStream.range(0, count)
            .mapToObj(index -> new TrackCatalog("trk-%03d".formatted(index),
                "Utwór %d".formatted(index), "Wykonawca %d".formatted(index)))
            .toList();
        trackCatalogRepository.saveAll(tracks);
    }

    private void awaitStatus(long executionId, String expectedStatus) {

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> assertThat(enrichmentService.status(executionId).status())
                .isEqualTo(expectedStatus));
    }

    private SpotifyTrackMetadata spotifyMetadata(String spotifyId) {

        return new SpotifyTrackMetadata(spotifyId, "Tytuł " + spotifyId, "Wykonawca " + spotifyId,
            "Album", 2020, 200000, 50, false, "https://img/" + spotifyId, "ISRC-" + spotifyId);
    }
}
