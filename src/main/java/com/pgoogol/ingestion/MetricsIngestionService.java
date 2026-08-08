package com.pgoogol.ingestion;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.ManualMetrics;
import com.pgoogol.catalog.ManualMetricsRepository;
import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.enrichment.metrics.ManualMetricsApplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Import metryk wgrywanych ręcznie z CSV (D24) — tymczasowe wejście na czas,
 * gdy Spotify nie oddaje już {@code audio-features}. Nie zakłada niczego
 * w bibliotece: wiersz dla utworu spoza katalogu jest pomijany z raportem,
 * bo biblioteka jedzie ze Spotify (M2.1/M2.2), a plik ma tylko dołożyć metryki.
 *
 * <p>Dopasowanie idzie po {@code spotify_id}, a gdy go brak albo nie ma takiego
 * utworu — po ISRC. ISRC identyfikuje nagranie, więc metryki trafiają wtedy do
 * wszystkich jego wydań w katalogu.</p>
 */
@Service
public class MetricsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MetricsIngestionService.class);

    private final MetricsCsvParser parser;
    private final TrackCatalogRepository trackCatalogRepository;
    private final ManualMetricsRepository manualMetricsRepository;
    private final ManualMetricsApplier applier;

    public MetricsIngestionService(MetricsCsvParser parser,
                                   TrackCatalogRepository trackCatalogRepository,
                                   ManualMetricsRepository manualMetricsRepository,
                                   ManualMetricsApplier applier) {

        this.parser = parser;
        this.trackCatalogRepository = trackCatalogRepository;
        this.manualMetricsRepository = manualMetricsRepository;
        this.applier = applier;
    }

    @Transactional
    public MetricsIngestReport ingest(InputStream csv, @Nullable String source) {

        MetricsParseResult parsed = parser.parse(csv);
        CatalogIndex index = index(parsed.rows());
        List<MatchedRow> matched = parsed.rows().stream()
            .map(row -> new MatchedRow(row, resolve(row, index)))
            .toList();

        Map<String, ManualMetrics> saved = save(matched, source);
        List<RowError> skipped = matched.stream()
            .filter(row -> row.match().tracks().isEmpty())
            .map(row -> new RowError(row.row().line(),
                "brak takiego utworu w katalogu — zaimportuj najpierw playlistę ze Spotify"))
            .toList();
        int matchedByIsrc = (int) matched.stream().filter(row -> row.match().byIsrc()).count();

        MetricsIngestReport report = new MetricsIngestReport(
            saved.size(), matchedByIsrc, skipped, parsed.errors());
        log.info("Import metryk zakończony: applied={}, matchedByIsrc={}, skipped={}, failed={}",
            report.applied(), report.matchedByIsrc(), report.skipped().size(), report.failed().size());
        return report;
    }

    private Map<String, ManualMetrics> save(List<MatchedRow> matched, @Nullable String source) {

        Map<String, ManualMetrics> existing = existingMetrics(matched);
        Map<String, ManualMetrics> working = new LinkedHashMap<>();
        Instant importedAt = Instant.now();
        matched.forEach(row -> row.match().tracks().forEach(track -> {
            // gatunek przed metrykami — korekta half-time pyta o genre_family
            applyGenreFamily(track, row.row().genreFamily());
            ManualMetrics metrics = working.computeIfAbsent(track.getSpotifyId(),
                spotifyId -> existing.getOrDefault(spotifyId, new ManualMetrics(track)));
            overwrite(metrics, row.row().metrics(), source, importedAt);
            applier.apply(track, metrics);
            warnOnCamelotMismatch(row.row().line(), track, metrics);
        }));
        manualMetricsRepository.saveAll(working.values());
        return working;
    }

    /**
     * Camelot z pliku zostaje surową wartością (D24), ale logika miksowania liczy
     * go z {@code musical_key} (D25) — jeśli oba są znane i się rozjeżdżają, wiersz
     * i utwór najpewniej opisują inne nagranie. Nie przerywamy importu: metryki bywają
     * dobre mimo literówki w jednej kolumnie, a decyzja należy do DJ-a.
     */
    private void warnOnCamelotMismatch(long line, TrackCatalog track, ManualMetrics metrics) {

        Optional<CamelotKey> fromFile = CamelotKey.ofLabel(metrics.getCamelot());
        Optional<CamelotKey> fromKey = CamelotKey.ofMusicalKey(track.getMusicalKey());
        if (fromFile.isEmpty() || fromKey.isEmpty() || fromFile.equals(fromKey)) {
            return;
        }
        log.warn("Wiersz {}: Camelot z pliku ({}) nie zgadza się z tonacją utworu {} ({} → {}) "
                + "— sprawdź, czy wiersz opisuje to nagranie",
            line, fromFile.orElseThrow().label(), track.getSpotifyId(),
            track.getMusicalKey(), fromKey.orElseThrow().label());
    }

    /**
     * Rodzina gatunkowa z pliku tylko wypełnia lukę: bez niej utwór po imporcie nie ma
     * slotu wieczoru (D9) ani korekty half-time, a na wzbogacenie AI może czekać długo.
     * Gdy gatunek już jest, plik go nie rusza — właścicielem {@code genre_family}
     * zostaje LLM (D8/D11), który nadpisze wartość przy najbliższym wzbogacaniu.
     */
    private void applyGenreFamily(TrackCatalog track, @Nullable GenreFamily fromFile) {

        if (Objects.isNull(track.getGenreFamily()) && Objects.nonNull(fromFile)) {
            track.setGenreFamily(fromFile);
        }
    }

    /**
     * Plik jest źródłem prawdy dla metryk, więc kolumny, których w nim nie ma,
     * kasują poprzednią wartość — inaczej po zmianie eksportu zostałyby w bazie
     * sieroty nie do wyjaśnienia. Projekcja na katalog nadpisuje tylko to,
     * co niepuste, więc BPM z poprzedniego źródła nie znika bez powodu.
     */
    private void overwrite(ManualMetrics metrics, TrackMetrics values,
                           @Nullable String source, Instant importedAt) {

        metrics.setBpm(values.bpm());
        metrics.setMusicalKey(values.musicalKey());
        metrics.setCamelot(values.camelot());
        metrics.setDanceability(values.danceability());
        metrics.setEnergy(values.energy());
        metrics.setValence(values.valence());
        metrics.setAcousticness(values.acousticness());
        metrics.setInstrumentalness(values.instrumentalness());
        metrics.setSpeechiness(values.speechiness());
        metrics.setLiveness(values.liveness());
        metrics.setLoudnessDb(values.loudnessDb());
        metrics.setTimeSignature(values.timeSignature());
        metrics.setSource(source);
        metrics.setImportedAt(importedAt);
    }

    private Map<String, ManualMetrics> existingMetrics(List<MatchedRow> matched) {

        Set<String> spotifyIds = matched.stream()
            .flatMap(row -> row.match().tracks().stream())
            .map(TrackCatalog::getSpotifyId)
            .collect(Collectors.toSet());
        return spotifyIds.isEmpty()
            ? Map.of()
            : manualMetricsRepository.findBySpotifyIdIn(spotifyIds).stream()
                .collect(Collectors.toMap(ManualMetrics::getSpotifyId, Function.identity()));
    }

    private Match resolve(ParsedMetrics row, CatalogIndex index) {

        Optional<TrackCatalog> direct = Optional.ofNullable(row.spotifyId()).map(index.byId()::get);
        if (direct.isPresent()) {
            return new Match(List.of(direct.get()), false);
        }
        List<TrackCatalog> byIsrc = Optional.ofNullable(row.isrc())
            .map(index.byIsrc()::get)
            .orElseGet(List::of);
        return new Match(byIsrc, !byIsrc.isEmpty());
    }

    private CatalogIndex index(List<ParsedMetrics> rows) {

        Set<String> spotifyIds = values(rows, ParsedMetrics::spotifyId);
        Set<String> isrcs = values(rows, ParsedMetrics::isrc);
        Map<String, TrackCatalog> byId = spotifyIds.isEmpty()
            ? Map.of()
            : trackCatalogRepository.findAllById(spotifyIds).stream()
                .collect(Collectors.toMap(TrackCatalog::getSpotifyId, Function.identity()));
        Map<String, List<TrackCatalog>> byIsrc = isrcs.isEmpty()
            ? Map.of()
            : trackCatalogRepository.findByIsrcInIgnoreCase(isrcs).stream()
                .collect(Collectors.groupingBy(track -> track.getIsrc().toUpperCase(Locale.ROOT)));
        return new CatalogIndex(byId, byIsrc);
    }

    private Set<String> values(List<ParsedMetrics> rows, Function<ParsedMetrics, String> field) {

        return rows.stream().map(field).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private record CatalogIndex(Map<String, TrackCatalog> byId,
                                Map<String, List<TrackCatalog>> byIsrc) {

    }

    private record Match(List<TrackCatalog> tracks, boolean byIsrc) {

    }

    private record MatchedRow(ParsedMetrics row, Match match) {

    }
}
