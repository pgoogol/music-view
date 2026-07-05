package com.pgoogol.enrichment.llm;

import java.util.List;

/**
 * Wynik analizy partii utworów + łączne zużycie tokenów (pomiar kosztu —
 * DoD M1.5, sekcja ryzyk PLAN.md).
 */
public record TrackAnalysisResult(List<TrackAnalysis> analyses, long inputTokens, long outputTokens) {

}
