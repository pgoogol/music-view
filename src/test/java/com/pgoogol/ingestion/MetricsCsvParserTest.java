package com.pgoogol.ingestion;

import com.pgoogol.catalog.GenreFamily;
import com.pgoogol.catalog.GenreFamilyMapper;
import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser CSV z metrykami (D24) na próbce w formacie eksportu z analizatora
 * playlist: komplet metryk, dopasowanie po ISRC, wiersze do odrzucenia.
 */
class MetricsCsvParserTest {

    private final MetricsCsvParser parser = new MetricsCsvParser(
        new CsvReader(), new CsvHeaderResolver(), new SpotifyTrackIdParser(),
        new MetricValueParser(), new GenreFamilyMapper());

    @Test
    void parse_whenSampleFile_readsMetricsAndReportsUnusableRows() {

        // given
        InputStream csv = sample();

        // when
        MetricsParseResult result = parser.parse(csv);

        // then
        assertThat(result.rows()).hasSize(3);
        assertThat(result.errors()).extracting(RowError::line).containsExactly(4L, 5L);
        assertThat(result.errors().get(0).reason()).contains("Spotify Track Id");
        assertThat(result.errors().get(1).reason()).contains("bez metryk");
    }

    @Test
    void parse_whenRowHasFullMetrics_mapsEveryColumnToDomainScale() {

        // given
        InputStream csv = sample();

        // when
        ParsedMetrics row = parser.parse(csv).rows().get(0);

        // then
        assertThat(row.spotifyId()).isEqualTo("2c7nzxJYmPtkimDdrhcfJx");
        assertThat(row.isrc()).isEqualTo("ES71G2337397");
        TrackMetrics metrics = row.metrics();
        assertThat(metrics.bpm()).isEqualByComparingTo("96");
        assertThat(metrics.musicalKey()).isEqualTo("G minor");
        assertThat(metrics.camelot()).isEqualTo("6A");
        assertThat(metrics.danceability()).isEqualByComparingTo("0.66");
        assertThat(metrics.energy()).isEqualByComparingTo("0.89");
        assertThat(metrics.valence()).isEqualByComparingTo("0.88");
        assertThat(metrics.loudnessDb()).isEqualByComparingTo("-6");
        assertThat(metrics.timeSignature()).isEqualTo(4);
    }

    @Test
    void parse_whenGenreColumnsPresent_derivesGenreFamilyForCatalog() {

        // given
        InputStream csv = sample();

        // when
        MetricsParseResult result = parser.parse(csv);

        // then — „timba, salsa" to rodzina LATIN (D8), potrzebna do korekty half-time
        assertThat(result.rows()).first()
            .satisfies(row -> assertThat(row.genreFamily()).isEqualTo(GenreFamily.LATIN));
    }

    @Test
    void parse_whenNoGenreColumn_leavesGenreFamilyForLlm() {

        // given
        InputStream csv = csv("""
            Spotify Track Id,BPM
            2c7nzxJYmPtkimDdrhcfJx,96
            """);

        // when
        MetricsParseResult result = parser.parse(csv);

        // then
        assertThat(result.rows()).first()
            .satisfies(row -> assertThat(row.genreFamily()).isNull());
    }

    @Test
    void parse_whenOnlyIsrcColumnFilled_matchesRowByRecording() {

        // given
        InputStream csv = sample();

        // when
        ParsedMetrics row = parser.parse(csv).rows().get(1);

        // then — ISRC z eksportu bywa małymi literami, dopasowanie idzie po wersalikach
        assertThat(row.spotifyId()).isNull();
        assertThat(row.isrc()).isEqualTo("USUL10110340");
    }

    @Test
    void parse_whenNoIdentityColumn_failsWithMissingColumns() {

        // given
        InputStream csv = csv("BPM,Energy\n96,89\n");

        // when + then
        assertThatThrownBy(() -> parser.parse(csv))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Spotify Track Id albo ISRC");
    }

    @Test
    void parse_whenNoMetricColumn_failsWithMissingColumns() {

        // given
        InputStream csv = csv("Spotify Track Id,Song\n2c7nzxJYmPtkimDdrhcfJx,La Lámpara\n");

        // when + then
        assertThatThrownBy(() -> parser.parse(csv))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("metrykami");
    }

    @Test
    void parse_whenSpotifyUriInsteadOfBareId_readsTrackId() {

        // given
        InputStream csv = csv("""
            Track URI,BPM
            spotify:track:2c7nzxJYmPtkimDdrhcfJx,96
            """);

        // when
        MetricsParseResult result = parser.parse(csv);

        // then
        assertThat(result.rows()).singleElement()
            .satisfies(row -> assertThat(row.spotifyId()).isEqualTo("2c7nzxJYmPtkimDdrhcfJx"));
    }

    @Test
    void parse_whenValuesInFractionScale_keepsThemAsIs() {

        // given — eksport oddający cechy w skali 0..1 zamiast procentów
        InputStream csv = csv("""
            Spotify Track Id,Danceability,Energy
            2c7nzxJYmPtkimDdrhcfJx,0.66,0.89
            """);

        // when
        TrackMetrics metrics = parser.parse(csv).rows().get(0).metrics();

        // then
        assertThat(metrics.danceability()).isEqualByComparingTo(new BigDecimal("0.66"));
        assertThat(metrics.energy()).isEqualByComparingTo(new BigDecimal("0.89"));
    }

    @Test
    void parse_whenQuoteInsideFieldNotEscaped_readsRowsInsteadOfFailingWholeFile() {

        // given — eksport analizatora playlist nie escapuje cudzysłowu w nazwie
        // wytwórni; ścisły parser wywracał się tu na całym pliku
        InputStream csv = csv("""
            Spotify Track Id,BPM,Label,ISRC
            2c7nzxJYmPtkimDdrhcfJx,96,"Héctor Acosta "El Torito"",ES71G2337397
            1BwrMGGhPA6GarWIYaFrW8,104,"UMLE - Machete",USUL10110340
            """);

        // when
        MetricsParseResult result = parser.parse(csv);

        // then — felerny wiersz wchodzi normalnie, kolejne też
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).extracting(ParsedMetrics::spotifyId)
            .containsExactly("2c7nzxJYmPtkimDdrhcfJx", "1BwrMGGhPA6GarWIYaFrW8");
        assertThat(result.rows()).extracting(row -> row.metrics().bpm())
            .containsExactly(new BigDecimal("96.00"), new BigDecimal("104.00"));
    }

    @Test
    void parse_whenBrokenQuotingShiftsColumns_reportsRowErrorAndReadsTheRest() {

        // given — cudzysłów z przecinkiem w środku pola rozjeżdża kolumny wiersza,
        // więc identyfikator utworu przestaje być czytelny
        InputStream csv = csv("""
            Label,Spotify Track Id,BPM
            "Wytwórnia "Bum, Bum" S.A.",2c7nzxJYmPtkimDdrhcfJx,96
            "UMLE - Machete",1BwrMGGhPA6GarWIYaFrW8,104
            """);

        // when
        MetricsParseResult result = parser.parse(csv);

        // then
        assertThat(result.errors()).singleElement()
            .satisfies(error -> assertThat(error.line()).isEqualTo(1));
        assertThat(result.rows()).singleElement()
            .satisfies(row -> assertThat(row.spotifyId()).isEqualTo("1BwrMGGhPA6GarWIYaFrW8"));
    }

    private InputStream sample() {

        return getClass().getClassLoader().getResourceAsStream("test-data/metryki-sample.csv");
    }

    private InputStream csv(String content) {

        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
