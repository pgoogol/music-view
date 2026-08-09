package com.pgoogol.library;

import java.util.List;

/**
 * Przegląd biblioteki (M4.3) — odpowiedź na pytanie „co ja właściwie mam".
 * Wszystkie rozkłady mają ten sam kształt {@link Bucket}, żeby front rysował
 * je jednym komponentem zamiast pięciu prawie identycznych.
 *
 * <p>{@code bpmSources} jest tu najważniejszą liczbą, nie ozdobą: mówi, ile
 * biblioteki stoi na faktach (manual / AcousticBrainz / Deezer), a ile na
 * estymacie LLM — czyli podaje na bieżąco wskaźnik, który D19 uczynił
 * kryterium decyzji o {@code AudioAnalyzer}.</p>
 */
public record LibraryOverview(
    long catalogTracks,
    long libraryTracks,
    long tracksWithMetrics,
    long metadataMissing,
    long audioMissing,
    long aiMissing,
    long lyricsMissing,
    List<Bucket> genres,
    List<Bucket> tempoClasses,
    List<Bucket> energies,
    List<Bucket> bpmSources,
    List<Bucket> ratings,
    List<Bucket> bpmHistogram,
    List<Bucket> topArtists,
    List<Bucket> monthlyGrowth) {

    /** Jeden słupek rozkładu: etykieta i liczba utworów. */
    public record Bucket(String label, long count) { }
}
