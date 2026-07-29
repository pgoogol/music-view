package com.pgoogol.enrichment.metrics;

import com.pgoogol.catalog.BpmSource;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.enrichment.TempoClassifier;
import com.pgoogol.enrichment.bpm.HalfTimeCorrector;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Rzutuje metryki wgrane ręcznie (D24) na pola katalogu, których używa reszta
 * aplikacji: bpm (+ {@code bpm_source=MANUAL}, korekta half-time, tempo_class),
 * tonacja, danceability i energia. Projekcja jest idempotentna i odtwarzalna —
 * ten sam kod biegnie przy imporcie CSV i przy wzbogacaniu (grupa AUDIO).
 *
 * <p>Energia w katalogu jest tekstem (D11), bo tak liczy ją LLM i tak filtruje
 * front; zmierzoną wartość 0..1 progujemy więc na {@code low/medium/high},
 * a surowa liczba zostaje w {@code manual_metrics} do podglądu.</p>
 */
@Component
public class ManualMetricsApplier {

    static final String LOW = "low";
    static final String MEDIUM = "medium";
    static final String HIGH = "high";

    private static final BigDecimal LOW_UPPER = new BigDecimal("0.40");
    private static final BigDecimal MEDIUM_UPPER = new BigDecimal("0.70");

    private final HalfTimeCorrector halfTimeCorrector;
    private final TempoClassifier tempoClassifier;

    public ManualMetricsApplier(HalfTimeCorrector halfTimeCorrector, TempoClassifier tempoClassifier) {

        this.halfTimeCorrector = halfTimeCorrector;
        this.tempoClassifier = tempoClassifier;
    }

    public void apply(TrackCatalog track, ManualMetrics metrics) {

        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(metrics, "metrics");
        Optional.ofNullable(metrics.getBpm()).ifPresent(bpm -> applyBpm(track, bpm));
        Optional.ofNullable(metrics.getMusicalKey()).ifPresent(track::setMusicalKey);
        Optional.ofNullable(metrics.getDanceability()).ifPresent(track::setDanceability);
        Optional.ofNullable(metrics.getEnergy()).map(this::energyLevel).ifPresent(track::setEnergy);
    }

    /** Zmierzona energia 0..1 na skalę słowną katalogu (D11). */
    public String energyLevel(BigDecimal energy) {

        Objects.requireNonNull(energy, "energy");
        if (energy.compareTo(LOW_UPPER) < 0) {
            return LOW;
        }
        return energy.compareTo(MEDIUM_UPPER) < 0 ? MEDIUM : HIGH;
    }

    private void applyBpm(TrackCatalog track, BigDecimal rawBpm) {

        int corrected = halfTimeCorrector.correct(
            track.getGenreFamily(), rawBpm.setScale(0, RoundingMode.HALF_UP).intValueExact());
        track.setBpm(corrected);
        track.setBpmSource(BpmSource.MANUAL);
        track.setTempoClass(tempoClassifier.classify(corrected));
    }
}
