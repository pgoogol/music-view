package com.pgoogol.enrichment.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;
import java.util.Objects;

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
    @DefaultValue("0.2") double temperature) {

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
