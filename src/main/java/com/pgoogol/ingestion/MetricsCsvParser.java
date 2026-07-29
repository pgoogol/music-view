package com.pgoogol.ingestion;

import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.GenreFamilyMapper;
import com.pgoogol.common.ValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parser CSV z metrykami wgrywanymi ręcznie (D24). Nagłówki rozpoznawane
 * elastycznie — plik ma zwykle więcej kolumn, niż potrzebujemy (metadane utworu
 * bierzemy ze Spotify, gatunki z LLM-a), więc czytamy tylko cechy audio
 * i identyfikator utworu. Wiersz bez identyfikatora albo bez ani jednej metryki
 * trafia do {@link RowError} i nie przerywa importu.
 */
@Component
public class MetricsCsvParser {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .build();

    private static final List<String> ID_COLUMNS =
        List.of("spotify track id", "spotify id", "track id", "track uri", "spotify uri", "uri");
    private static final List<String> ISRC_COLUMNS = List.of("isrc");
    private static final List<String> GENRE_COLUMNS = List.of("genres", "genre");
    private static final List<String> PARENT_GENRE_COLUMNS =
        List.of("parent genres", "parent genre");
    private static final List<String> BPM_COLUMNS = List.of("bpm", "tempo");
    private static final List<String> KEY_COLUMNS = List.of("key", "musical key");
    private static final List<String> CAMELOT_COLUMNS = List.of("camelot", "camelot key");
    private static final List<String> DANCE_COLUMNS = List.of("dance", "danceability");
    private static final List<String> ENERGY_COLUMNS = List.of("energy");
    private static final List<String> VALENCE_COLUMNS = List.of("valence", "happiness");
    private static final List<String> ACOUSTIC_COLUMNS = List.of("acoustic", "acousticness");
    private static final List<String> INSTRUMENTAL_COLUMNS =
        List.of("instrumental", "instrumentalness");
    private static final List<String> SPEECH_COLUMNS = List.of("speech", "speechiness");
    private static final List<String> LIVE_COLUMNS = List.of("live", "liveness");
    private static final List<String> LOUDNESS_COLUMNS =
        List.of("loud (db)", "loudness (db)", "loudness", "loud");
    private static final List<String> TIME_SIGNATURE_COLUMNS =
        List.of("time signature", "timesignature");

    private static final List<List<String>> METRIC_COLUMNS = List.of(
        BPM_COLUMNS, KEY_COLUMNS, CAMELOT_COLUMNS, DANCE_COLUMNS, ENERGY_COLUMNS, VALENCE_COLUMNS,
        ACOUSTIC_COLUMNS, INSTRUMENTAL_COLUMNS, SPEECH_COLUMNS, LIVE_COLUMNS, LOUDNESS_COLUMNS,
        TIME_SIGNATURE_COLUMNS);

    private final CsvHeaderResolver headerResolver;
    private final SpotifyTrackIdParser trackIdParser;
    private final MetricValueParser valueParser;
    private final GenreFamilyMapper genreFamilyMapper;

    public MetricsCsvParser(CsvHeaderResolver headerResolver, SpotifyTrackIdParser trackIdParser,
                            MetricValueParser valueParser, GenreFamilyMapper genreFamilyMapper) {

        this.headerResolver = headerResolver;
        this.trackIdParser = trackIdParser;
        this.valueParser = valueParser;
        this.genreFamilyMapper = genreFamilyMapper;
    }

    public MetricsParseResult parse(InputStream input) {

        Objects.requireNonNull(input, "input");
        try (CSVParser csvParser = CSVParser.parse(
                new InputStreamReader(input, StandardCharsets.UTF_8), CSV_FORMAT)) {

            Map<String, Integer> headers = headerResolver.normalize(csvParser);
            requireIdentity(headers);
            requireAnyMetric(headers);

            List<ParsedMetrics> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            csvParser.forEach(row -> parseRow(row, headers, rows, errors));
            return new MetricsParseResult(List.copyOf(rows), List.copyOf(errors));
        } catch (IOException | UncheckedIOException ex) {
            throw new ValidationException("CSV_UNREADABLE", "Nie udało się odczytać pliku CSV");
        }
    }

    private void parseRow(CSVRecord row, Map<String, Integer> headers,
                          List<ParsedMetrics> rows, List<RowError> errors) {

        long line = row.getRecordNumber();
        Optional<String> spotifyId = headerResolver.value(row, headers, ID_COLUMNS)
            .flatMap(trackIdParser::parse);
        Optional<String> isrc = headerResolver.value(row, headers, ISRC_COLUMNS)
            .map(value -> value.replace("-", "").toUpperCase(Locale.ROOT));
        if (spotifyId.isEmpty() && isrc.isEmpty()) {
            errors.add(new RowError(line, "brak Spotify Track Id i ISRC — nie ma po czym dopasować"));
            return;
        }
        TrackMetrics metrics = readMetrics(row, headers);
        if (metrics.isEmpty()) {
            errors.add(new RowError(line, "wiersz bez metryk"));
            return;
        }
        rows.add(new ParsedMetrics(line, spotifyId.orElse(null), isrc.orElse(null),
            readGenreFamily(row, headers).orElse(null), metrics));
    }

    /** Gatunki ze szczegółowych przed nadrzędnymi — „timba" mówi więcej niż „Latin". */
    private Optional<GenreFamily> readGenreFamily(CSVRecord row, Map<String, Integer> headers) {

        return genreFamilyMapper.map(List.of(
            cell(row, headers, GENRE_COLUMNS).orElse(""),
            cell(row, headers, PARENT_GENRE_COLUMNS).orElse("")));
    }

    private TrackMetrics readMetrics(CSVRecord row, Map<String, Integer> headers) {

        return new TrackMetrics(
            cell(row, headers, BPM_COLUMNS).flatMap(valueParser::bpm).orElse(null),
            cell(row, headers, KEY_COLUMNS).flatMap(valueParser::musicalKey).orElse(null),
            cell(row, headers, CAMELOT_COLUMNS).flatMap(valueParser::camelot).orElse(null),
            score(row, headers, DANCE_COLUMNS),
            score(row, headers, ENERGY_COLUMNS),
            score(row, headers, VALENCE_COLUMNS),
            score(row, headers, ACOUSTIC_COLUMNS),
            score(row, headers, INSTRUMENTAL_COLUMNS),
            score(row, headers, SPEECH_COLUMNS),
            score(row, headers, LIVE_COLUMNS),
            cell(row, headers, LOUDNESS_COLUMNS).flatMap(valueParser::loudness).orElse(null),
            cell(row, headers, TIME_SIGNATURE_COLUMNS).flatMap(valueParser::timeSignature).orElse(null));
    }

    private BigDecimal score(CSVRecord row, Map<String, Integer> headers, List<String> columns) {

        return cell(row, headers, columns).flatMap(valueParser::score).orElse(null);
    }

    private Optional<String> cell(CSVRecord row, Map<String, Integer> headers, List<String> columns) {

        return headerResolver.value(row, headers, columns);
    }

    private void requireIdentity(Map<String, Integer> headers) {

        if (headerResolver.find(headers, ID_COLUMNS).isEmpty()
            && headerResolver.find(headers, ISRC_COLUMNS).isEmpty()) {
            throw new ValidationException("CSV_MISSING_COLUMNS",
                "Plik CSV nie zawiera kolumny identyfikującej utwór: Spotify Track Id albo ISRC");
        }
    }

    private void requireAnyMetric(Map<String, Integer> headers) {

        boolean anyMetric = METRIC_COLUMNS.stream()
            .anyMatch(columns -> headerResolver.find(headers, columns).isPresent());
        if (!anyMetric) {
            throw new ValidationException("CSV_MISSING_COLUMNS",
                "Plik CSV nie zawiera żadnej kolumny z metrykami (BPM, Key, Camelot, Energy, Dance…)");
        }
    }
}
