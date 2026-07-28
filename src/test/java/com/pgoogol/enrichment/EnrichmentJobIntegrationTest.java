package com.pgoogol.enrichment;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @BeforeEach
    void stubSources() {

        analyzedBatches.clear();
        failOnSecondChunk.set(false);
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
    void restart_whenJobFailedMidRun_finishesRemainingTracksWithoutReprocessing() {

        // given — awaria LLM na drugim chunku (utwory trk-005…trk-009)
        seedSkeletons(10);
        failOnSecondChunk.set(true);
        long failedExecutionId = enrichmentService.start(
            EnrichmentScope.MISSING, EnumSet.allOf(FieldGroup.class), List.of());
        awaitStatus(failedExecutionId, "FAILED");

        // częściowy postęp widoczny w bazie: pierwszy chunk (5) zapisany
        assertThat(trackCatalogRepository.findAll().stream()
            .filter(track -> track.getGenreFamily() != null))
            .extracting(TrackCatalog::getSpotifyId)
            .containsExactlyInAnyOrder("trk-000", "trk-001", "trk-002", "trk-003", "trk-004");

        // when
        long restartedExecutionId = enrichmentService.restart(failedExecutionId);
        awaitStatus(restartedExecutionId, "COMPLETED");

        // then — komplet w bazie, bez duplikatów przetwarzania: utwory z pierwszego
        // chunka nie wracają do LLM po restarcie
        assertThat(trackCatalogRepository.findAll())
            .hasSize(10)
            .allSatisfy(track -> assertThat(track.getGenreFamily()).isEqualTo(GenreFamily.LATIN));
        List<String> analyzedAfterRestart = analyzedBatches.stream()
            .skip(1)
            .flatMap(List::stream)
            .toList();
        assertThat(analyzedAfterRestart)
            .containsExactlyInAnyOrder("trk-005", "trk-006", "trk-007", "trk-008", "trk-009");
        assertThat(analyzedBatches.stream().flatMap(List::stream))
            .doesNotHaveDuplicates();
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
