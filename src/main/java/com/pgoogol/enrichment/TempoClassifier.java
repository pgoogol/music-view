package com.pgoogol.enrichment;

import com.pgoogol.catalog.TempoClass;
import org.springframework.stereotype.Component;

/**
 * Uniwersalne pasma tempa (§16.2): slow &lt;100 | medium 100–128 |
 * fast 128–160 | very_fast &gt;160 BPM.
 */
@Component
public class TempoClassifier {

    private static final int SLOW_UPPER = 100;
    private static final int MEDIUM_UPPER = 128;
    private static final int FAST_UPPER = 160;

    public TempoClass classify(int bpm) {

        if (bpm < SLOW_UPPER) {
            return TempoClass.SLOW;
        }
        if (bpm < MEDIUM_UPPER) {
            return TempoClass.MEDIUM;
        }
        if (bpm <= FAST_UPPER) {
            return TempoClass.FAST;
        }
        return TempoClass.VERY_FAST;
    }
}
