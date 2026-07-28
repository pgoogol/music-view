package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser CSV z eksportu Exportify / pliku własnego (M1.2). Wymagane kolumny:
 * Spotify URI, tytuł, wykonawca (nagłówki rozpoznawane elastycznie); album
 * opcjonalny. Wiersze niepoprawne trafiają do {@link RowError}, nie przerywają importu.
 */
@Component
public class CsvTrackParser {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .build();

    private static final Pattern TRACK_ID = Pattern.compile(
        "^(?:spotify:track:|https?://open\\.spotify\\.com/track/)?([0-9A-Za-z]{22})(?:\\?\\S*)?$");

    private static final List<String> URI_COLUMNS = List.of("track uri", "spotify uri", "uri");
    private static final List<String> TITLE_COLUMNS = List.of("track name", "track", "title");
    private static final List<String> ARTIST_COLUMNS = List.of("artist name(s)", "artist name", "artist");
    private static final List<String> ALBUM_COLUMNS = List.of("album name", "album");

    public CsvParseResult parse(InputStream input) {

        Objects.requireNonNull(input, "input");
        try (CSVParser csvParser = CSVParser.parse(
                new InputStreamReader(input, StandardCharsets.UTF_8), CSV_FORMAT)) {

            Map<String, Integer> headers = normalizedHeaders(csvParser);
            int uriColumn = requiredColumn(headers, URI_COLUMNS, "Spotify URI");
            int titleColumn = requiredColumn(headers, TITLE_COLUMNS, "tytuł utworu");
            int artistColumn = requiredColumn(headers, ARTIST_COLUMNS, "wykonawca");
            Optional<Integer> albumColumn = findColumn(headers, ALBUM_COLUMNS);

            List<ParsedTrack> tracks = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            csvParser.forEach(row ->
                parseRow(row, uriColumn, titleColumn, artistColumn, albumColumn, tracks, errors));
            return new CsvParseResult(List.copyOf(tracks), List.copyOf(errors));
        } catch (IOException | UncheckedIOException ex) {
            throw new ValidationException("CSV_UNREADABLE", "Nie udało się odczytać pliku CSV");
        }
    }

    private void parseRow(CSVRecord row, int uriColumn, int titleColumn, int artistColumn,
                          Optional<Integer> albumColumn, List<ParsedTrack> tracks, List<RowError> errors) {

        long line = row.getRecordNumber();
        int lastRequiredColumn = Math.max(uriColumn, Math.max(titleColumn, artistColumn));
        if (row.size() <= lastRequiredColumn) {
            errors.add(new RowError(line, "niekompletny wiersz — za mało kolumn"));
            return;
        }
        Optional<String> spotifyId = extractTrackId(row.get(uriColumn));
        if (spotifyId.isEmpty()) {
            errors.add(new RowError(line,
                "nieprawidłowe Spotify URI: '%s'".formatted(row.get(uriColumn))));
            return;
        }
        String title = row.get(titleColumn);
        if (isBlank(title)) {
            errors.add(new RowError(line, "brak tytułu utworu"));
            return;
        }
        String artist = row.get(artistColumn);
        if (isBlank(artist)) {
            errors.add(new RowError(line, "brak wykonawcy"));
            return;
        }
        String album = albumColumn
            .filter(column -> row.size() > column)
            .map(row::get)
            .filter(value -> !isBlank(value))
            .orElse(null);
        tracks.add(new ParsedTrack(spotifyId.get(), title, artist, album));
    }

    private Optional<String> extractTrackId(String rawUri) {

        if (isBlank(rawUri)) {
            return Optional.empty();
        }
        Matcher matcher = TRACK_ID.matcher(rawUri.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Map<String, Integer> normalizedHeaders(CSVParser csvParser) {

        Map<String, Integer> headerMap = csvParser.getHeaderMap();
        if (Objects.isNull(headerMap) || headerMap.isEmpty()) {
            throw new ValidationException("CSV_EMPTY", "Plik CSV nie zawiera nagłówka");
        }
        return headerMap.entrySet().stream().collect(Collectors.toMap(
            entry -> entry.getKey().replace("\ufeff", "").trim().toLowerCase(Locale.ROOT),
            Map.Entry::getValue,
            (first, second) -> first));
    }

    private int requiredColumn(Map<String, Integer> headers, List<String> candidates, String description) {

        return findColumn(headers, candidates).orElseThrow(() -> new ValidationException(
            "CSV_MISSING_COLUMNS",
            "Plik CSV nie zawiera wymaganej kolumny: %s".formatted(description)));
    }

    private Optional<Integer> findColumn(Map<String, Integer> headers, List<String> candidates) {

        return candidates.stream()
            .map(headers::get)
            .filter(Objects::nonNull)
            .findFirst();
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
