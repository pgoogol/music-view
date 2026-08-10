package com.pgoogol.enrichment.llm;

import org.springframework.lang.Nullable;

/**
 * @param maxTokens sufit odpowiedzi dla tego wywołania; {@code null} = wartość
 *                  z konfiguracji ({@code llm.max-tokens}). Tłumaczenie tekstu
 *                  potrzebuje innego sufitu niż opis utworu (D32), a sufit
 *                  odpowiedzi zajmuje pamięć modelu tak samo jak wejście —
 *                  na lokalnej karcie to różnica między działa a nie działa.
 */
public record LlmPrompt(String system, String user, @Nullable Integer maxTokens) {

    public LlmPrompt(String system, String user) {
        this(system, user, null);
    }

    public int maxTokensOr(int fallback) {
        return maxTokens == null ? fallback : maxTokens;
    }
}
