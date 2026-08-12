package com.pgoogol.library;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Przegląd biblioteki (M4.3, rozbudowa M5.4) — odpowiedź na pytanie „co ja
 * właściwie mam". Rozkłady mają wspólny kształt {@link Bucket}, żeby front
 * rysował je jednym komponentem zamiast kilkunastu prawie identycznych.
 *
 * <p>Pola siedzą w pięciu grupach zamiast w jednej płaskiej liście
 * (D36): ekran ma pięć stref czytania, a konstruktor z dwudziestoma
 * argumentami tego samego typu {@code List<Bucket>} to zaproszenie do
 * przestawienia dwóch rozkładów miejscami bez żadnego błędu kompilacji.</p>
 *
 * <p>{@code bpmSources} jest tu nadal najważniejszą liczbą, nie ozdobą: mówi,
 * ile biblioteki stoi na faktach (manual / AcousticBrainz / Deezer), a ile na
 * estymacie LLM — czyli podaje na bieżąco wskaźnik, który D19 uczynił
 * kryterium decyzji o {@code AudioAnalyzer}.</p>
 */
public record LibraryOverview(
    Scale scale,
    Quality quality,
    Sound sound,
    Timeline timeline,
    Taste taste,
    List<RecentTrack> recentlyAdded) {

    /** Jeden słupek rozkładu: etykieta i liczba utworów. */
    public record Bucket(String label, long count) { }

    /** Komórka macierzy tempo × energia — dwa wymiary naraz, stąd własny kształt. */
    public record MatrixCell(String tempoClass, String energy, long count) { }

    /** Średnia cecha audio z metryk ręcznych (D24), skala 0..1. */
    public record Metric(String label, double value) { }

    /** Próbka ostatnio dodanych utworów — okładki są jedyną grafiką na ekranie. */
    public record RecentTrack(
        String spotifyId,
        @Nullable String title,
        @Nullable String artist,
        @Nullable String albumImageUrl,
        Instant addedAt) { }

    /**
     * Skala zbioru — same utwory. Playlisty i sety świadomie nie wchodzą na ten
     * ekran (D36): przegląd odpowiada na „co mam w bibliotece", a nie „co z tego
     * ułożyłem"; to drugie ma własne zakładki.
     *
     * <p>Średnie są {@code null} dla pustego zbioru — zero znaczyłoby „średnie
     * tempo 0 BPM", a to nieprawda.</p>
     */
    public record Scale(
        long catalogTracks,
        long libraryTracks,
        long tracksWithMetrics,
        long libraryDurationMs,
        long distinctArtists,
        long distinctAlbums,
        @Nullable Double averageBpm,
        @Nullable Double averageDurationMs,
        @Nullable Double averagePopularity,
        @Nullable Double averageDanceability,
        long tracksWithDanceability) { }

    /** Kompletność danych: czego brakuje i skąd wiemy to, co wiemy. */
    public record Quality(
        long metadataMissing,
        long audioMissing,
        long aiMissing,
        List<Bucket> bpmSources,
        List<Bucket> confidences) { }

    /** Brzmienie katalogu — wszystko, co odpowiada na „co ja właściwie gram". */
    public record Sound(
        List<Bucket> genres,
        List<Bucket> styles,
        List<Bucket> tempoClasses,
        List<Bucket> energies,
        List<Bucket> bpmHistogram,
        List<Bucket> camelotKeys,
        List<Bucket> durations,
        List<Bucket> popularity,
        List<Bucket> explicitness,
        /** Metrum z metryk ręcznych (D24) — obejmuje tylko utwory z pliku. */
        List<Bucket> timeSignatures,
        List<MatrixCell> tempoEnergy,
        List<Metric> audioProfile) { }

    /** Biblioteka w czasie: kiedy rosła i z jakich roczników się składa. */
    public record Timeline(
        List<Bucket> monthlyGrowth,
        List<Bucket> decades) { }

    /** Kto i co dominuje w zbiorze — wykonawcy, albumy, własne tagi i oceny. */
    public record Taste(
        List<Bucket> topArtists,
        List<Bucket> topAlbums,
        List<Bucket> topTags,
        List<Bucket> ratings) { }
}
