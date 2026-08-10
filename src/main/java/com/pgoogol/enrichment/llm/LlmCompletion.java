package com.pgoogol.enrichment.llm;

/**
 * Odpowiedź LLM + zużycie tokenów — podstawa pomiaru kosztu (DoD M1.5,
 * sekcja ryzyk w PLAN.md).
 *
 * @param truncated provider przerwał generowanie na limicie tokenów
 *                  ({@code finish_reason: length} / {@code stop_reason: max_tokens}).
 *                  Bez tego ucięta odpowiedź wygląda w logu jak kaprys modelu,
 *                  a jest ustawieniem do poprawienia (D32).
 */
public record LlmCompletion(String content, long inputTokens, long outputTokens,
                            boolean truncated) {

    public LlmCompletion(String content, long inputTokens, long outputTokens) {
        this(content, inputTokens, outputTokens, false);
    }
}
