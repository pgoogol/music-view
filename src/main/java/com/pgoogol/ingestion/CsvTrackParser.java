package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parser CSV z eksportu Exportify / analizatora playlist / pliku własnego (M1.2).
 * Wymagane kolumny: identyfikator utworu (URI, link albo samo Spotify Track Id),
 * tytuł, wykonawca — nagłówki rozpoznawane elastycznie; album opcjonalny.
 * Wiersze niepoprawne trafiają do {@link RowError}, nie przerywają importu.
 */
@Component
public class CsvTrackParser {

    private static final List<String> URI_COLUMNS =
        List.of("track uri", "spotify uri", "uri", "spotify track id", "spotify id", "track id");
    private static final List<String> TITLE_COLUMNS = List.of("track name", "track", "title", "song");
    private static final List<String> ARTIST_COLUMNS = List.of("artist name(s)", "artist name", "artist");
    private static final List<String> ALBUM_COLUMNS = List.of("album name", "album");

    private final CsvReader csvReader;
    private final SpotifyTrackIdParser trackIdParser;
    private final CsvHeaderResolver headerResolver;

    public CsvTrackParser(CsvReader csvReader, SpotifyTrackIdParser trackIdParser,
                          CsvHeaderResolver headerResolver) {

        this.csvReader = csvReader;
        this.trackIdParser = trackIdParser;
        this.headerResolver = headerResolver;
    }

    public CsvParseResult parse(InputStream input) {

        Objects.requireNonNull(input, "input");
        try (CSVParser csvParser = csvReader.open(input)) {

            Map<String, Integer> headers = headerResolver.normalize(csvParser);
            int uriColumn = headerResolver.require(headers, URI_COLUMNS, "Spotify URI albo Track Id");
            int titleColumn = headerResolver.require(headers, TITLE_COLUMNS, "tytuł utworu");
            int artistColumn = headerResolver.require(headers, ARTIST_COLUMNS, "wykonawca");
            Optional<Integer> albumColumn = headerResolver.find(headers, ALBUM_COLUMNS);

            List<ParsedTrack> tracks = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            csvReader.forEachRow(csvParser, row ->
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
        Optional<String> spotifyId = trackIdParser.parse(row.get(uriColumn));
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

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
