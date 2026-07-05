package com.pgoogol.enrichment.bpm;

import com.pgoogol.catalog.GenreFamily;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Sanity-check half-time (§16.1, D6) — wspólny dla kaskady BPM (M1.4)
 * i estymat LLM (M1.5): latin + BPM &lt; 100 → podwojenie, o ile wynik
 * pozostaje w wiarygodnym zakresie tanecznym.
 */
@Component
public class HalfTimeCorrector {

    private static final int HALF_TIME_THRESHOLD = 100;
    private static final int MAX_PLAUSIBLE_BPM = 210;

    public int correct(@Nullable GenreFamily genreFamily, int bpm) {

        boolean latin = Objects.equals(genreFamily, GenreFamily.LATIN);
        int doubled = bpm * 2;
        if (latin && bpm < HALF_TIME_THRESHOLD && doubled <= MAX_PLAUSIBLE_BPM) {
            return doubled;
        }
        return bpm;
    }
}
