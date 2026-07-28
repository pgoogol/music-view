package com.pgoogol.playlist;

import com.pgoogol.catalog.GenreFamily;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DjSlotCalculatorTest {

    private final DjSlotCalculator calculator = new DjSlotCalculator();

    @ParameterizedTest(name = "bpm={0}, energia={1}, gatunek={2} → {3}")
    @CsvSource({
        // ballada na oddech niezależnie od energii
        "68,  low,    POP,        BREAK",
        "70,  high,   ROCK,       BREAK",
        // rozgrzewka: wolno albo spokojnie
        "88,  medium, LATIN,      WARMUP",
        "140, low,    ELECTRONIC, WARMUP",
        // szczyt: wysoka energia i szybkie tempo
        "128, high,   ELECTRONIC, PEAK",
        "186, high,   LATIN,      PEAK",
        // parkietowe gatunki wchodzą w szczyt już od 110
        "112, high,   DISCO_POLO, PEAK",
        "112, high,   ROCK,       CLOSING",
        // zamknięcie: energia jest, tempo już zeszło
        "104, high,   POP,        CLOSING",
        // środek wieczoru
        "110, medium, ROCK,       MIDDLE",
    })
    void calculate_whenFactsKnown_placesTrackInEveningSlot(int bpm, String energy,
                                                           GenreFamily genreFamily,
                                                           DjSlot expected) {

        // when
        Optional<DjSlot> slot = calculator.calculate(bpm, energy, genreFamily);

        // then
        assertThat(slot).contains(expected);
    }

    @Test
    void calculate_whenOnlyEnergyKnown_usesEnergyAlone() {

        // when + then — utwór bez BPM (kaskada D6 pusta), ale po fazie AI
        assertThat(calculator.calculate(null, "high", GenreFamily.LATIN)).contains(DjSlot.PEAK);
        assertThat(calculator.calculate(null, "low", GenreFamily.POP)).contains(DjSlot.WARMUP);
        assertThat(calculator.calculate(null, "medium", null)).contains(DjSlot.MIDDLE);
    }

    @Test
    void calculate_whenOnlyBpmKnown_usesTempoAlone() {

        // when + then — po fazie AUDIO, przed AI
        assertThat(calculator.calculate(150, null, null)).contains(DjSlot.MIDDLE);
        assertThat(calculator.calculate(80, null, null)).contains(DjSlot.WARMUP);
    }

    @Test
    void calculate_whenTrackNotEnrichedYet_returnsNoSlot() {

        // when
        Optional<DjSlot> slot = calculator.calculate(null, null, null);

        // then
        assertThat(slot).isEmpty();
    }

    @Test
    void calculate_whenEnergyWrittenDifferently_isCaseInsensitive() {

        // when + then
        assertThat(calculator.calculate(130, "HIGH", GenreFamily.POP)).contains(DjSlot.PEAK);
        assertThat(calculator.calculate(130, " Low ", GenreFamily.POP)).contains(DjSlot.WARMUP);
    }
}
