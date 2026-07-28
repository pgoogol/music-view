package com.pgoogol.enrichment;

import com.pgoogol.catalog.TempoClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TempoClassifierTest {

    private final TempoClassifier classifier = new TempoClassifier();

    @ParameterizedTest
    @CsvSource({
        "99, SLOW",
        "100, MEDIUM",
        "127, MEDIUM",
        "128, FAST",
        "160, FAST",
        "161, VERY_FAST"
    })
    void classify_whenBpmOnBandBoundary_returnsBandFromConcept(int bpm, TempoClass expected) {

        // given / when
        TempoClass tempoClass = classifier.classify(bpm);

        // then
        assertThat(tempoClass).isEqualTo(expected);
    }
}
