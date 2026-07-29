package com.pgoogol.enrichment.bpm;

/**
 * Raport pokrycia BPM per źródło (DoD M1.4; pełny raport na realnej
 * bibliotece powstaje w M1.9). Metryki ręczne (D24) są osobnym źródłem —
 * inaczej nie dałoby się zobaczyć, ile biblioteki stoi na wgranym pliku.
 */
public record BpmCoverageReport(int fromManual, int fromAcousticBrainz, int fromDeezer,
                                int withoutBpm) {

    public int total() {
        return fromManual + fromAcousticBrainz + fromDeezer + withoutBpm;
    }
}
