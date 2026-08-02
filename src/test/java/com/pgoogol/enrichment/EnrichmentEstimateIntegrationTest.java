package com.pgoogol.enrichment;

import com.pgoogol.TestcontainersConfiguration;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Szacunek zlecenia i zakres OUTDATED (M5.1/D28) na realnej bazie — liczby
 * biorą się z zapytań, więc test bez Postgresa nic by nie dowiódł.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "llm.model=test-model-2",
    // wersja promptu musi mieć plik w resources — bierzemy istniejącą v1
    "llm.prompt-version=v1",
    "llm.max-tracks-per-job=5",
    "llm.cost.input-per-1m=1.00",
    "llm.cost.output-per-1m=5.00"
})
class EnrichmentEstimateIntegrationTest {

    @Autowired
    private EnrichmentService enrichmentService;

    @Autowired
    private TrackCatalogRepository trackCatalogRepository;

    @BeforeEach
    void seed() {

        // opisane bieżącym modelem i promptem — aktualne
        saveEnriched("sp-aktualny-1", "test-model-2", 1);
        saveEnriched("sp-aktualny-2", "test-model-2", 1);
        // opisane starszym modelem albo starszą wersją promptu — do przeliczenia
        saveEnriched("sp-stary-model", "test-model-1", 1);
        saveEnriched("sp-stary-prompt", "test-model-2", 0);
        // nigdy nieopisany — to zakres MISSING, nie OUTDATED
        trackCatalogRepository.save(new TrackCatalog("sp-nowy", "Nowy", "Nieznany"));
    }

    @AfterEach
    void cleanDatabase() {
        trackCatalogRepository.deleteAll();
    }

    @Test
    @DisplayName("OUTDATED bierze utwory opisane innym modelem albo inną wersją promptu")
    void estimate_outdatedCountsStaleEstimatesOnly() {

        EnrichmentEstimate estimate = enrichmentService.estimate(
            EnrichmentScope.OUTDATED, Set.of(FieldGroup.AI), List.of());

        assertThat(estimate.trackCount()).isEqualTo(2);
        assertThat(estimate.aiTracks()).isEqualTo(2);
    }

    @Test
    @DisplayName("koszt liczy się ze stawek z konfiguracji i pomiaru M1.9")
    void estimate_usesConfiguredRates() {

        EnrichmentEstimate estimate = enrichmentService.estimate(
            EnrichmentScope.OUTDATED, Set.of(FieldGroup.AI), List.of());

        // 140 tok. wej. × $1/1M + 120 tok. wyj. × $5/1M = $0.00074 na utwór — dokładnie
        // tyle, ile wyszło w próbie generalnej M1.9; dla dwóch utworów $0.00148
        assertThat(estimate.estimatedCost()).isEqualByComparingTo("0.0015");
    }

    @Test
    @DisplayName("bez grupy AI zlecenie nic nie kosztuje — fakty jadą z darmowych źródeł")
    void estimate_factsOnlyCostNothing() {

        EnrichmentEstimate estimate = enrichmentService.estimate(
            EnrichmentScope.MISSING, Set.of(FieldGroup.AUDIO), List.of());

        assertThat(estimate.aiTracks()).isZero();
        assertThat(estimate.estimatedCost()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("MISSING liczy utwory z brakiem w którejkolwiek zaznaczonej grupie")
    void estimate_missingCombinesSelectedGroupsWithOr() {

        EnrichmentEstimate onlyAi = enrichmentService.estimate(
            EnrichmentScope.MISSING, Set.of(FieldGroup.AI), List.of());
        EnrichmentEstimate aiAndAudio = enrichmentService.estimate(
            EnrichmentScope.MISSING, Set.of(FieldGroup.AI, FieldGroup.AUDIO), List.of());

        assertThat(onlyAi.trackCount()).isEqualTo(1);
        assertThat(aiAndAudio.trackCount()).isGreaterThanOrEqualTo(onlyAi.trackCount());
    }

    @Test
    @DisplayName("zlecenie ponad limit nie startuje i mówi, ile obejmuje")
    void start_whenAboveLimit_isRejectedWithCount() {

        List<String> tooMany = List.of("sp-1", "sp-2", "sp-3", "sp-4", "sp-5", "sp-6");

        assertThatThrownBy(() -> enrichmentService.start(
                EnrichmentScope.SELECTED, Set.of(FieldGroup.AI), tooMany))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("6")
            .hasMessageContaining("5")
            .hasMessageContaining("llm.max-tracks-per-job");
    }

    @Test
    @DisplayName("OUTDATED odrzuca grupy faktów — nie zależą od modelu ani promptu")
    void estimate_outdatedRejectsFactGroups() {

        assertThatThrownBy(() -> enrichmentService.estimate(
                EnrichmentScope.OUTDATED, Set.of(FieldGroup.AI, FieldGroup.AUDIO), List.of()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("wyłącznie estymaty");
    }

    @Test
    @DisplayName("OUTDATED nie przyjmuje listy utworów")
    void estimate_outdatedRejectsExplicitIds() {

        assertThatThrownBy(() -> enrichmentService.estimate(
                EnrichmentScope.OUTDATED, Set.of(FieldGroup.AI), List.of("sp-nowy")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("nie przyjmuje listy");
    }

    private void saveEnriched(String spotifyId, String model, int enrichVersion) {

        TrackCatalog track = new TrackCatalog(spotifyId, "Utwór " + spotifyId, "Wykonawca");
        track.setGenreFamily(GenreFamily.LATIN);
        track.setStyle("salsa");
        track.setLyricsTheme("temat");
        track.setDescriptionPl("opis");
        track.setEnergy("high");
        track.setEnrichedAt(Instant.now());
        track.setModelUsed(model);
        track.setEnrichVersion(enrichVersion);
        trackCatalogRepository.save(track);
    }
}
