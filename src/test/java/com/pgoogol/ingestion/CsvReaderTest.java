package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wspólne wejście CSV importów (M1.2, D24) — tolerancja na felerne cudzysłowy
 * z eksportów oraz komunikat z numerem wiersza, gdy odczyt naprawdę pada.
 */
class CsvReaderTest {

    private final CsvReader reader = new CsvReader();

    @Test
    void forEachRow_whenQuoteInsideFieldNotEscaped_keepsColumnsAlignedAndReadsFurtherRows()
        throws IOException {

        // given
        InputStream input = stream("""
            id,label,isrc
            2c7nzxJYmPtkimDdrhcfJx,"Héctor Acosta "El Torito"",USBMS2200028
            1BwrMGGhPA6GarWIYaFrW8,"UMLE - Machete",USUL10110340
            """);
        List<CSVRecord> rows = new ArrayList<>();

        // when
        try (CSVParser csvParser = reader.open(input)) {
            reader.forEachRow(csvParser, rows::add);
        }

        // then — kolumny za felernym polem nie przesuwają się
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get(0)).isEqualTo("2c7nzxJYmPtkimDdrhcfJx");
        assertThat(rows.get(0).get(2)).isEqualTo("USBMS2200028");
        assertThat(rows.get(1).get(2)).isEqualTo("USUL10110340");
    }

    @Test
    void forEachRow_whenFileEndsInsideQuotedField_readsRowInsteadOfFailing() throws IOException {

        // given
        InputStream input = stream("id,label\n2c7nzxJYmPtkimDdrhcfJx,\"urwana wytwórnia");
        List<CSVRecord> rows = new ArrayList<>();

        // when
        try (CSVParser csvParser = reader.open(input)) {
            reader.forEachRow(csvParser, rows::add);
        }

        // then
        assertThat(rows).singleElement()
            .satisfies(row -> assertThat(row.get(1)).isEqualTo("urwana wytwórnia"));
    }

    @Test
    void forEachRow_whenStreamBreaks_reportsLineNumberOfTheFailure() throws IOException {

        // given — strumień pada w trzecim wierszu pliku
        InputStream input = failingAfter("""
            id,label
            a,pierwszy
            b,dru""");

        // when + then
        try (CSVParser csvParser = reader.open(input)) {
            assertThatThrownBy(() -> reader.forEachRow(csvParser, row -> { }))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("wierszu 3");
        }
    }

    private InputStream stream(String csv) {

        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Strumień, który oddaje {@code prefix}, a potem zgłasza awarię odczytu. */
    private InputStream failingAfter(String prefix) {

        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {

            private int position;

            @Override
            public int read() throws IOException {
                if (position >= bytes.length) {
                    throw new IOException("dysk odmówił współpracy");
                }
                return bytes[position++];
            }
        };
    }
}
