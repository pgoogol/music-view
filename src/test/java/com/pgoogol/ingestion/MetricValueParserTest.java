package com.pgoogol.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Komórki CSV z metrykami (D24) — skale, zapisy tonacji i wartości do odrzucenia.
 */
class MetricValueParserTest {

    private final MetricValueParser parser = new MetricValueParser();

    @Test
    void score_whenPercentScale_convertsToFraction() {

        // when + then
        assertThat(parser.score("89")).contains(new BigDecimal("0.890"));
        assertThat(parser.score("0")).contains(new BigDecimal("0.000"));
        assertThat(parser.score("100")).contains(new BigDecimal("1.000"));
    }

    @Test
    void score_whenFractionScale_keepsValue() {

        // when + then
        assertThat(parser.score("0.85")).contains(new BigDecimal("0.850"));
        assertThat(parser.score("0,85")).contains(new BigDecimal("0.850"));
        assertThat(parser.score("85%")).contains(new BigDecimal("0.850"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "brak", "-5", "9999"})
    void score_whenUnusableValue_returnsEmpty(String raw) {

        // when + then
        assertThat(parser.score(raw)).isEmpty();
    }

    @Test
    void bpm_whenOutsidePlausibleRange_returnsEmpty() {

        // when + then
        assertThat(parser.bpm("96")).contains(new BigDecimal("96.00"));
        assertThat(parser.bpm("0")).isEmpty();
        assertThat(parser.bpm("999")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "G minor,G minor",
        "G,G major",
        "F#/G♭ minor,F# minor",
        "C♯/D♭,C# major",
        "a#/b♭ minor,A# minor",
    })
    void musicalKey_whenAnyExportSpelling_normalizesToAcousticBrainzFormat(String raw, String expected) {

        // when + then
        assertThat(parser.musicalKey(raw)).contains(expected);
    }

    @Test
    void musicalKey_whenNumericPitchClass_returnsEmpty() {

        // given — część eksportów podaje tonację liczbą, a z niej nie wynika tryb

        // when + then
        assertThat(parser.musicalKey("7")).isEmpty();
    }

    @Test
    void camelot_whenValidWheelPosition_normalizesLetterToUppercase() {

        // when + then
        assertThat(parser.camelot("6a")).contains("6A");
        assertThat(parser.camelot("11B")).contains("11B");
        assertThat(parser.camelot("13A")).isEmpty();
        assertThat(parser.camelot("G minor")).isEmpty();
    }

    @Test
    void loudness_whenNegativeDecibels_keepsSign() {

        // when + then
        assertThat(parser.loudness("-6")).contains(new BigDecimal("-6.00"));
        assertThat(parser.loudness("-99")).isEmpty();
    }

    @Test
    void timeSignature_whenOutsideRange_returnsEmpty() {

        // when + then
        assertThat(parser.timeSignature("4")).contains(4);
        assertThat(parser.timeSignature("0")).isEmpty();
    }
}
