package com.pgoogol.enrichment.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Locale;

/**
 * Wybór implementacji {@link LlmClient} wyłącznie z konfiguracji (D15).
 */
@Configuration
public class LlmClientConfig {

    @Bean
    public LlmClient llmClient(LlmProperties properties, RestClient.Builder restClientBuilder) {

        return switch (properties.provider().toLowerCase(Locale.ROOT)) {
            case "anthropic" -> new AnthropicLlmClient(restClientBuilder, properties);
            case "openai", "openai-compatible" -> new OpenAiCompatibleLlmClient(restClientBuilder, properties);
            default -> throw new IllegalStateException(
                "Nieznany provider LLM w konfiguracji: '%s' (obsługiwane: openai, anthropic)"
                    .formatted(properties.provider()));
        };
    }
}
