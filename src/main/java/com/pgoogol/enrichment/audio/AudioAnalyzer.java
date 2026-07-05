package com.pgoogol.enrichment.audio;

import com.pgoogol.catalog.TrackCatalog;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Przyszła własna analiza audio (preview) — wycofana z zakresu Etapu 1 (D6);
 * interfejs zostaje jako punkt zaczepienia. Decyzja o implementacji zapada
 * po raporcie pokrycia BPM w M1.9.
 */
public interface AudioAnalyzer {

    Optional<AudioAnalysis> analyze(TrackCatalog track);

    record AudioAnalysis(Integer bpm, String musicalKey, BigDecimal danceability) {

    }
}
