package com.pgoogol.enrichment.audio;

import com.pgoogol.catalog.TrackCatalog;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stub (M1.4): zawsze brak wyniku — kaskada BPM kończy się na Deezerze.
 */
// TODO: analiza previewu (np. librosa/TarsosDSP po stronie skryptu) — do odblokowania
//  osobną decyzją w DECYZJE.md, jeśli raport pokrycia M1.9 wykaże istotne braki BPM.
@Component
public class NoopAudioAnalyzer implements AudioAnalyzer {

    @Override
    public Optional<AudioAnalysis> analyze(TrackCatalog track) {
        return Optional.empty();
    }
}
