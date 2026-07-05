package com.pgoogol.enrichment.llm;

/**
 * Abstrakcja nad dowolnym providerem LLM (D15) — warstwa AI nie zna
 * konkretnego API. Implementacja wybierana w {@link LlmClientConfig}
 * na podstawie konfiguracji.
 */
public interface LlmClient {

    LlmCompletion complete(LlmPrompt prompt);
}
