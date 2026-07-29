package com.pgoogol.ingestion;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Komplet metryk jednego utworu odczytany z CSV (D24). Cechy w skali 0..1,
 * BPM surowy (bez korekty half-time), tonacja w zapisie „G minor".
 * Każde pole jest opcjonalne — eksporty różnią się zestawem kolumn.
 */
public record TrackMetrics(
    @Nullable BigDecimal bpm,
    @Nullable String musicalKey,
    @Nullable String camelot,
    @Nullable BigDecimal danceability,
    @Nullable BigDecimal energy,
    @Nullable BigDecimal valence,
    @Nullable BigDecimal acousticness,
    @Nullable BigDecimal instrumentalness,
    @Nullable BigDecimal speechiness,
    @Nullable BigDecimal liveness,
    @Nullable BigDecimal loudnessDb,
    @Nullable Integer timeSignature) {

    /** Wiersz bez ani jednej metryki to zwykle pomyłka co do pliku, nie brak danych. */
    public boolean isEmpty() {

        return Stream.of(bpm, musicalKey, camelot, danceability, energy, valence, acousticness,
                instrumentalness, speechiness, liveness, loudnessDb, timeSignature)
            .allMatch(Objects::isNull);
    }
}
