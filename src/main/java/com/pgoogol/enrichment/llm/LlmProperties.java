package com.pgoogol.enrichment.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider, model i wersja promptu wyłącznie w konfiguracji (D15) —
 * nigdy w kodzie. {@code provider}: {@code openai} (i wszystkie API zgodne
 * z OpenAI: OpenRouter, Groq, Mistral, lokalne) lub {@code anthropic}.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    @DefaultValue("openai") String provider,
    String baseUrl,
    String apiKey,
    String model,
    @DefaultValue("v1") String promptVersion,
    @DefaultValue("5") int batchSize,
    @DefaultValue("2") int requestsPerSecond,
    @DefaultValue("2048") int maxTokens,
    @DefaultValue("0.2") double temperature,
    @DefaultValue("500") int maxTracksPerJob,
    Cost cost,
    Lyrics lyrics) {

    private static final Pattern VERSION_DIGITS = Pattern.compile("\\d+");

    public LlmProperties {
        cost = Objects.requireNonNullElse(cost, Cost.UNKNOWN);
        lyrics = Objects.requireNonNullElse(lyrics, Lyrics.DEFAULTS);
    }

    /**
     * Stawki providera (USD za 1M tokenów) — potrzebne, żeby pokazać koszt
     * zlecenia przed jego startem (D28). Puste = nie znamy cennika i mówimy
     * to wprost zamiast zgadywać.
     */
    public record Cost(BigDecimal inputPer1m, BigDecimal outputPer1m) {

        static final Cost UNKNOWN = new Cost(null, null);

        public boolean isKnown() {
            return Objects.nonNull(inputPer1m) && Objects.nonNull(outputPer1m);
        }
    }

    /**
     * Tłumaczenie tekstów utworów (M6.1/D32) — własna wersja promptu, bo tekst
     * tłumaczy się z innych powodów niż opisuje utwór, i własny limit długości
     * wejścia: tekst piosenki bywa dłuższy niż cała partia metadanych.
     */
    public record Lyrics(
        @DefaultValue("v1") String promptVersion,
        @DefaultValue("6000") int maxChars) {

        static final Lyrics DEFAULTS = new Lyrics("v1", 6000);

        /** Wersja promptu jako liczba do {@code track_lyrics.prompt_version}. */
        public Optional<Integer> promptVersionNumber() {
            return versionNumber(promptVersion);
        }
    }

    /**
     * Wersja promptu jako liczba do {@code track_catalog.enrich_version} (D3).
     * Trzyma się tu, a nie w {@code TrackEnricher}, bo tej samej wartości używa
     * zakres {@code OUTDATED} przy szukaniu utworów do przeliczenia (D28).
     */
    public Optional<Integer> promptVersionNumber() {
        return versionNumber(promptVersion);
    }

    private static Optional<Integer> versionNumber(String version) {

        Matcher matcher = VERSION_DIGITS.matcher(Objects.requireNonNullElse(version, ""));
        return matcher.find() ? Optional.of(Integer.valueOf(matcher.group())) : Optional.empty();
    }

    public String resolvedBaseUrl() {

        if (Objects.nonNull(baseUrl) && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "anthropic" -> "https://api.anthropic.com";
            default -> "https://api.openai.com";
        };
    }
}
