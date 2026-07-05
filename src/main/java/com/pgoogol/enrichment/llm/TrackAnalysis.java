package com.pgoogol.enrichment.llm;

import com.pgoogol.catalog.GenreFamily;
import org.springframework.lang.Nullable;

/**
 * Strukturalny wynik analizy AI jednego utworu (grupa AI — D11);
 * {@code bpmEstimate} tylko gdy jawnie zażądano (kaskada M1.4 pusta).
 */
public record TrackAnalysis(
    String spotifyId,
    String style,
    GenreFamily genreFamily,
    String lyricsTheme,
    String descriptionPl,
    String energy,
    String confidence,
    @Nullable Integer bpmEstimate) {

}
