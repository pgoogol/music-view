package com.pgoogol.enrichment.bpm;

import com.pgoogol.catalog.BpmSource;

/**
 * Wynik kaskady BPM (D6) — wartość po ewentualnej korekcie half-time
 * + źródło do audytu w {@code track_catalog.bpm_source}.
 */
public record BpmResolution(int bpm, BpmSource source) {

}
