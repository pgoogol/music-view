package com.pgoogol.playlist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SetCurveTest {

    @ParameterizedTest
    @EnumSource(SetCurve.class)
    @DisplayName("każdy profil obejmuje cztery fazy D9 w kolejności i sumuje się do całości")
    void shouldCoverWholeEveningInOrder(SetCurve curve) {

        List<SetCurve.Phase> phases = curve.phases();

        assertThat(phases).extracting(SetCurve.Phase::slot)
            .containsExactly(DjSlot.WARMUP, DjSlot.MIDDLE, DjSlot.PEAK, DjSlot.CLOSING);
        assertThat(phases.stream().mapToDouble(SetCurve.Phase::share).sum())
            .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("klub ma dłuższy szczyt niż wesele, wesele dłuższą rozgrzewkę niż klub")
    void shouldDifferInShapeBetweenProfiles() {

        assertThat(share(SetCurve.CLUB, DjSlot.PEAK))
            .isGreaterThan(share(SetCurve.WEDDING, DjSlot.PEAK));
        assertThat(share(SetCurve.WEDDING, DjSlot.WARMUP))
            .isGreaterThan(share(SetCurve.CLUB, DjSlot.WARMUP));
    }

    @Test
    @DisplayName("rozpoznaje nazwę profilu bez względu na wielkość liter, puste odrzuca")
    void shouldParseProfileName() {

        assertThat(SetCurve.parse("club")).contains(SetCurve.CLUB);
        assertThat(SetCurve.parse("  WEDDING ")).contains(SetCurve.WEDDING);
        assertThat(SetCurve.parse("")).isEmpty();
        assertThat(SetCurve.parse(null)).isEmpty();
        assertThat(SetCurve.parse("dyskoteka")).isEmpty();
    }

    private double share(SetCurve curve, DjSlot slot) {

        return curve.phases().stream()
            .filter(phase -> phase.slot() == slot)
            .mapToDouble(SetCurve.Phase::share)
            .sum();
    }
}
