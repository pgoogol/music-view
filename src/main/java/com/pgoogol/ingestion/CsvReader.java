package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Wspólne otwieranie plików CSV importu — biblioteki (M1.2) i metryk (D24).
 *
 * <p>Format jest celowo wyrozumiały. Eksporty z analizatorów playlist potrafią
 * nie escapować cudzysłowu w środku pola (np. wytwórnia
 * {@code "Héctor Acosta "El Torito""}), a ścisły parser przerywa wtedy odczyt
 * <b>całego</b> pliku — jeden felerny wiersz kasował import tysiąca pozostałych.
 * Tolerancja na dane po zamykającym cudzysłowie i na urwany cudzysłów na końcu
 * pliku zamyka szkodę w obrębie jednego wiersza: dalsze wiersze wracają do
 * normalnego parsowania, a wiersz rozjechany na kolumnach odpada później
 * na walidacji wartości jako {@link RowError}.</p>
 */
@Component
public class CsvReader {

    private static final Logger log = LoggerFactory.getLogger(CsvReader.class);

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .setTrailingData(true)
        .setLenientEof(true)
        .get();

    /** Parser z wczytanym nagłówkiem; zamyka go wołający (try-with-resources). */
    public CSVParser open(InputStream input) {

        Objects.requireNonNull(input, "input");
        try {
            return CSVParser.parse(new InputStreamReader(input, StandardCharsets.UTF_8), FORMAT);
        } catch (IOException | UncheckedIOException ex) {
            log.warn("Nie udało się wczytać nagłówka pliku CSV", ex);
            throw new ValidationException("CSV_UNREADABLE", "Nie udało się odczytać pliku CSV");
        }
    }

    /**
     * Przechodzi po wierszach, a awarię odczytu zgłasza z numerem wiersza —
     * bez niego „nie udało się odczytać pliku" nie mówi DJ-owi, co poprawić.
     */
    public void forEachRow(CSVParser csvParser, Consumer<CSVRecord> rowConsumer) {

        Objects.requireNonNull(csvParser, "csvParser");
        Objects.requireNonNull(rowConsumer, "rowConsumer");
        try {
            csvParser.forEach(rowConsumer);
        } catch (UncheckedIOException ex) {
            long line = csvParser.getCurrentLineNumber();
            log.warn("Odczyt pliku CSV przerwany w wierszu {}", line, ex);
            throw new ValidationException("CSV_UNREADABLE",
                "Nie udało się odczytać pliku CSV — odczyt przerwał się w wierszu %d"
                    .formatted(line));
        }
    }
}
