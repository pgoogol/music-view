package com.pgoogol.enrichment.llm;

import org.springframework.lang.Nullable;

/**
 * Wynik tłumaczenia i interpretacji tekstu jednego utworu (M6.1/D32).
 *
 * @param sourceLanguage  język oryginału rozpoznany przez model (np. „hiszpański")
 * @param translationPl   tłumaczenie na polski
 * @param interpretationPl o czym utwór opowiada — kontekst, obrazy, nastrój
 */
public record LyricsTranslation(
    @Nullable String sourceLanguage,
    @Nullable String translationPl,
    @Nullable String interpretationPl,
    long inputTokens,
    long outputTokens) {

}
