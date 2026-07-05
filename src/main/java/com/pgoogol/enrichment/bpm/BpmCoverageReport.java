package com.pgoogol.enrichment.bpm;

/**
 * Raport pokrycia BPM per źródło (DoD M1.4; pełny raport na realnej
 * bibliotece powstaje w M1.9).
 */
public record BpmCoverageReport(int fromAcousticBrainz, int fromDeezer, int withoutBpm) {

    public int total() {
        return fromAcousticBrainz + fromDeezer + withoutBpm;
    }
}
