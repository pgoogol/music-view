package com.pgoogol.enrichment.metrics;

import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.TempoClass;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogFixtures;
import com.pgoogol.enrichment.TempoClassifier;
import com.pgoogol.enrichment.bpm.HalfTimeCorrector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Projekcja metryk z pliku (D24) na pola katalogu: BPM z korektą half-time,
 * tonacja, danceability i progowana energia.
 */
class ManualMetricsApplierTest {

    private final ManualMetricsApplier applier =
        new ManualMetricsApplier(new HalfTimeCorrector(), new TempoClassifier());

    @Test
    void apply_whenLatinTrackWithHalfTimeBpm_doublesBpmAndMarksSourceAsManual() {

        // given — eksporty podają salsę w half-timie (96), realne tempo parkietowe to 192
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-1");
        track.setGenreFamily(GenreFamily.LATIN);
        ManualMetrics metrics = metricsWithBpm(track, "96");

        // when
        applier.apply(track, metrics);

        // then
        assertThat(track.getBpm()).isEqualTo(192);
        assertThat(track.getBpmSource()).isEqualTo(BpmSource.MANUAL);
        assertThat(track.getTempoClass()).isEqualTo(TempoClass.VERY_FAST);
    }

    @Test
    void apply_whenGenreOutsideLatin_keepsBpmFromFile() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-2");
        track.setGenreFamily(GenreFamily.POP);
        ManualMetrics metrics = metricsWithBpm(track, "96.4");

        // when
        applier.apply(track, metrics);

        // then
        assertThat(track.getBpm()).isEqualTo(96);
        assertThat(track.getTempoClass()).isEqualTo(TempoClass.SLOW);
    }

    @Test
    void apply_whenFileCarriesKeyAndDanceability_copiesThemToCatalog() {

        // given
        TrackCatalog track = TrackCatalogFixtures.skeletonTrack("sp-3");
        ManualMetrics metrics = new ManualMetrics(track);
        metrics.setMusicalKey("G minor");
        metrics.setDanceability(new BigDecimal("0.660"));
        metrics.setEnergy(new BigDecimal("0.890"));

        // when
        applier.apply(track, metrics);

        // then
        assertThat(track.getMusicalKey()).isEqualTo("G minor");
        assertThat(track.getDanceability()).isEqualByComparingTo("0.66");
        assertThat(track.getEnergy()).isEqualTo("high");
    }

    @Test
    void apply_whenFileHasNoBpm_leavesCatalogValueUntouched() {

        // given — plik z samą tonacją nie może skasować BPM z innego źródła
        TrackCatalog track = TrackCatalogFixtures.enrichedTrack("sp-4");
        ManualMetrics metrics = new ManualMetrics(track);
        metrics.setMusicalKey("C major");

        // when
        applier.apply(track, metrics);

        // then
        assertThat(track.getBpm()).isEqualTo(92);
        assertThat(track.getBpmSource()).isEqualTo(BpmSource.ACOUSTICBRAINZ);
        assertThat(track.getMusicalKey()).isEqualTo("C major");
    }

    @ParameterizedTest
    @CsvSource({"0.10,low", "0.39,low", "0.40,medium", "0.69,medium", "0.70,high", "0.95,high"})
    void energyLevel_whenMeasuredValue_mapsToCatalogScale(String measured, String expected) {

        // when + then
        assertThat(applier.energyLevel(new BigDecimal(measured))).isEqualTo(expected);
    }

    private ManualMetrics metricsWithBpm(TrackCatalog track, String bpm) {

        ManualMetrics metrics = new ManualMetrics(track);
        metrics.setBpm(new BigDecimal(bpm));
        return metrics;
    }
}
