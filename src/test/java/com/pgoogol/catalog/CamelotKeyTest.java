package com.pgoogol.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CamelotKeyTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "Ab minor, 1A", "Eb minor, 2A", "Bb minor, 3A", "F minor, 4A",
        "C minor, 5A", "G minor, 6A", "D minor, 7A", "A minor, 8A",
        "E minor, 9A", "B minor, 10A", "F# minor, 11A", "Db minor, 12A",
        "B major, 1B", "F# major, 2B", "Db major, 3B", "Ab major, 4B",
        "Eb major, 5B", "Bb major, 6B", "F major, 7B", "C major, 8B",
        "G major, 9B", "D major, 10B", "A major, 11B", "E major, 12B"})
    @DisplayName("mapuje każdą z 24 tonacji na właściwą pozycję koła")
    void shouldMapEveryKeyToWheelPosition(String musicalKey, String expectedLabel) {

        Optional<CamelotKey> key = CamelotKey.ofMusicalKey(musicalKey);

        assertThat(key).isPresent();
        assertThat(key.orElseThrow().label()).isEqualTo(expectedLabel);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"D# minor", "Eb minor", "d# MINOR", "  Eb   minor  ", "D♯ minor"})
    @DisplayName("czyta zapisy enharmoniczne i niesforne jako tę samą tonację")
    void shouldAcceptEnharmonicSpellings(String musicalKey) {

        assertThat(CamelotKey.ofMusicalKey(musicalKey))
            .contains(new CamelotKey(2, true));
    }

    @Test
    @DisplayName("brak trybu czyta jako dur — tak samo jak normalizacja importu (D24)")
    void shouldTreatMissingModeAsMajor() {
        assertThat(CamelotKey.ofMusicalKey("C")).contains(new CamelotKey(8, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "H minor", "8A", "123", "minor"})
    @DisplayName("odrzuca wejście, które nie jest tonacją")
    void shouldRejectNonKeys(String raw) {
        assertThat(CamelotKey.ofMusicalKey(raw)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"8A, 8, true", "12b, 12, false", " 1A , 1, true", "10B, 10, false"})
    @DisplayName("czyta etykietę koła, także z odstępami i małą literą")
    void shouldParseLabel(String raw, int number, boolean minor) {
        assertThat(CamelotKey.ofLabel(raw)).contains(new CamelotKey(number, minor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0A", "13A", "8C", "A8", "8"})
    @DisplayName("odrzuca etykietę spoza koła")
    void shouldRejectLabelOutsideWheel(String raw) {
        assertThat(CamelotKey.ofLabel(raw)).isEmpty();
    }

    @Test
    @DisplayName("zgodne to ta sama tonacja, sąsiedzi ±1 i tonacja równoległa (D25)")
    void shouldReturnFourCompatiblePositions() {

        Set<CamelotKey> compatible = new CamelotKey(8, true).compatible();

        assertThat(compatible.stream().map(CamelotKey::label))
            .containsExactlyInAnyOrder("8A", "9A", "7A", "8B");
    }

    @Test
    @DisplayName("koło się zawija: sąsiadem 1A jest 12A, a sąsiadem 12A — 1A")
    void shouldWrapAroundTheWheel() {

        assertThat(new CamelotKey(1, true).compatible().stream().map(CamelotKey::label))
            .containsExactlyInAnyOrder("1A", "2A", "12A", "1B");
        assertThat(new CamelotKey(12, false).compatible().stream().map(CamelotKey::label))
            .containsExactlyInAnyOrder("12B", "1B", "11B", "12A");
    }

    @Test
    @DisplayName("zgodność jest symetryczna dla wszystkich 24 pozycji")
    void shouldBeSymmetric() {

        List<CamelotKey> all = allPositions().toList();

        assertThat(all).allSatisfy(key -> assertThat(key.compatible())
            .allSatisfy(other -> assertThat(other.isCompatibleWith(key))
                .as("%s ↔ %s", key.label(), other.label())
                .isTrue()));
    }

    @Test
    @DisplayName("każda pozycja koła ma zapis tonacji, który wraca na tę samą pozycję")
    void shouldRoundTripThroughMusicalKey() {

        assertThat(allPositions()).allSatisfy(key -> assertThat(key.musicalKeySpellings())
            .isNotEmpty()
            .allSatisfy(spelling -> assertThat(CamelotKey.ofMusicalKey(spelling))
                .as("zapis %s", spelling)
                .contains(key)));
    }

    @Test
    @DisplayName("zapisy tonacji podaje wielkimi literami — filtr porównuje z upper(musical_key)")
    void shouldReturnUppercaseSpellings() {

        assertThat(new CamelotKey(2, true).musicalKeySpellings())
            .containsExactlyInAnyOrder("D# MINOR", "EB MINOR");
    }

    private static Stream<CamelotKey> allPositions() {

        return IntStream.rangeClosed(1, 12)
            .boxed()
            .flatMap(number -> Stream.of(new CamelotKey(number, true), new CamelotKey(number, false)));
    }
}
