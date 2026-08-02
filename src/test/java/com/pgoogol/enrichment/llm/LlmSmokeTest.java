package com.pgoogol.enrichment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.enrichment.bpm.HalfTimeCorrector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-test manualny na realnym providerze LLM (DoD M1.5) — domyślnie pomijany.
 * Uruchomienie:
 * {@code MV_SMOKE=true LLM_PROVIDER=… LLM_API_KEY=… LLM_MODEL=… ./mvnw test -Dtest=LlmSmokeTest}
 * Opcjonalnie LLM_BASE_URL oraz stawki do wyliczenia kosztu:
 * LLM_COST_INPUT_PER_1M i LLM_COST_OUTPUT_PER_1M (USD za 1M tokenów).
 * Wynik (tokeny + koszt/utwór) wpisz do PLAN.md, sekcja ryzyk.
 */
class LlmSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "MV_SMOKE", matches = "true")
    @EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
    void analyze_tenDiverseRealTracks_returnsValidJsonAndMeasuresCost() {

        // given — 10 zróżnicowanych utworów: latino / rock / disco polo / inne
        List<TrackCatalog> tracks = List.of(
            track("4uLU6hMCjMI75M1A2tKUQC", "Vivir Mi Vida", "Marc Anthony", "3.0", 2013),
            track("2wJ0GDUC2n0NRDoTVBb2K1", "La Vida Es Un Carnaval", "Celia Cruz", "Mi Vida es Cantar", 1998),
            track("2b8fOow8UzyDFAE27YhOZM", "Propuesta Indecente", "Romeo Santos", "Fórmula, Vol. 2", 2013),
            track("7tFiyTwD0nx5a1eklYtX2J", "Bohemian Rhapsody", "Queen", "A Night At The Opera", 1975),
            track("2zYzyRzz6pRmhPzyfMEC8s", "Highway to Hell", "AC/DC", "Highway to Hell", 1979),
            track("smoke-akcent-oczy-001x", "Przez Twe Oczy Zielone", "Akcent", "Przekorny los", 2014),
            track("smoke-boys-szalona-01x", "Jesteś szalona", "Boys", "Jesteś szalona", 1996),
            track("0DiWol3AO6WpXZgp0goxAV", "One More Time", "Daft Punk", "Discovery", 2000),
            track("7cSY3TKQAr5oa9SciAXCBn", "I Will Survive", "Gloria Gaynor", "Love Tracks", 1978),
            track("5Z01UMMf7V1JbfY45QxZks", "Lose Yourself", "Eminem", "8 Mile", 2002));

        LlmProperties properties = new LlmProperties(
            env("LLM_PROVIDER", "openai"), env("LLM_BASE_URL", null), System.getenv("LLM_API_KEY"),
            System.getenv("LLM_MODEL"), "v1", 5, 1, 2048, 0.2, 500, null);
        LlmClient llmClient = new LlmClientConfig().llmClient(properties, RestClient.builder());
        TrackAnalysisService service = new TrackAnalysisService(llmClient,
            new TrackAnalysisPrompt(properties), new ObjectMapper(), new HalfTimeCorrector(),
            properties);

        // when
        TrackAnalysisResult result = service.analyze(tracks, true);

        // then — poprawny JSON i komplet pól; sensowność genre_family oceń na stdout
        result.analyses().forEach(analysis -> System.out.printf(
            "%s → genre=%s style=%s energy=%s conf=%s bpm=%s | %s%n",
            analysis.spotifyId(), analysis.genreFamily(), analysis.style(), analysis.energy(),
            analysis.confidence(), analysis.bpmEstimate(), analysis.descriptionPl()));
        printCost(result, tracks.size(), properties.model());

        assertThat(result.analyses()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(result.analyses()).allSatisfy(analysis -> {
            assertThat(analysis.genreFamily()).isNotNull();
            assertThat(analysis.descriptionPl()).isNotBlank();
        });
    }

    private void printCost(TrackAnalysisResult result, int trackCount, String model) {

        System.out.printf("Model %s: tokeny wejściowe=%d, wyjściowe=%d (%d utworów)%n",
            model, result.inputTokens(), result.outputTokens(), trackCount);
        Optional<Double> inputRate = rate("LLM_COST_INPUT_PER_1M");
        Optional<Double> outputRate = rate("LLM_COST_OUTPUT_PER_1M");
        if (inputRate.isPresent() && outputRate.isPresent()) {
            double cost = result.inputTokens() / 1_000_000.0 * inputRate.get()
                + result.outputTokens() / 1_000_000.0 * outputRate.get();
            System.out.printf("Koszt: $%.6f łącznie, $%.6f / utwór → wpisz do PLAN.md (ryzyka)%n",
                cost, cost / trackCount);
        } else {
            System.out.println("Podaj LLM_COST_INPUT_PER_1M i LLM_COST_OUTPUT_PER_1M, "
                + "aby wyliczyć koszt na utwór.");
        }
    }

    private Optional<Double> rate(String name) {
        return Optional.ofNullable(System.getenv(name)).map(Double::valueOf);
    }

    private String env(String name, String fallback) {
        return Objects.requireNonNullElse(System.getenv(name), fallback);
    }

    private TrackCatalog track(String spotifyId, String title, String artist, String album, int year) {

        TrackCatalog track = new TrackCatalog(spotifyId, title, artist);
        track.setAlbum(album);
        track.setYear(year);
        return track;
    }
}
