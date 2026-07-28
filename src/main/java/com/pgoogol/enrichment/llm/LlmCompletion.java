package com.pgoogol.enrichment.llm;

/**
 * Odpowiedź LLM + zużycie tokenów — podstawa pomiaru kosztu (DoD M1.5,
 * sekcja ryzyk w PLAN.md).
 */
public record LlmCompletion(String content, long inputTokens, long outputTokens) {

}
