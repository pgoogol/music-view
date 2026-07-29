package com.pgoogol.catalog;

/**
 * Źródło wartości BPM — audyt kaskady D6 rozszerzonej w D24:
 * metryki ręczne → AcousticBrainz → Deezer → LLM.
 */
public enum BpmSource {

    MANUAL,
    ACOUSTICBRAINZ,
    DEEZER,
    LLM
}
