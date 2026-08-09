package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CsvTrackParserTest {

    private final CsvTrackParser parser = new CsvTrackParser(
        new CsvReader(), new SpotifyTrackIdParser(), new CsvHeaderResolver());

    @Test
    void parse_whenExportifyStyleCsvGiven_returnsParsedTracks() {

        // given
        InputStream csv = toStream("""
            Track URI,Track Name,Artist Name(s),Album Name,Added At
            spotify:track:4uLU6hMCjMI75M1A2tKUQC,Vivir Mi Vida,Marc Anthony,3.0,2026-01-01
            spotify:track:1zHlj4dQ8ZAtrayhuDDmkY,Danza Kuduro,"Don Omar, Lucenzo",Meet The Orphans,2026-01-02
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.tracks()).containsExactly(
            new ParsedTrack("4uLU6hMCjMI75M1A2tKUQC", "Vivir Mi Vida", "Marc Anthony", "3.0"),
            new ParsedTrack("1zHlj4dQ8ZAtrayhuDDmkY", "Danza Kuduro", "Don Omar, Lucenzo",
                "Meet The Orphans"));
    }

    @Test
    void parse_whenTrackUrlInsteadOfUri_extractsSpotifyId() {

        // given
        InputStream csv = toStream("""
            Track URI,Track Name,Artist Name(s)
            https://open.spotify.com/track/3ZFTkvIE7kyPt6Nu3PEa7V?si=abc,La Gozadera,Gente De Zona
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.tracks()).extracting(ParsedTrack::spotifyId)
            .containsExactly("3ZFTkvIE7kyPt6Nu3PEa7V");
    }

    @Test
    void parse_whenUriInvalid_reportsRowErrorAndContinues() {

        // given
        InputStream csv = toStream("""
            Track URI,Track Name,Artist Name(s)
            spotify:local:abc,Lokalny utwór,Ktoś
            spotify:track:4uLU6hMCjMI75M1A2tKUQC,Vivir Mi Vida,Marc Anthony
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.tracks()).hasSize(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isEqualTo(1);
            assertThat(error.reason()).contains("Spotify URI");
        });
    }

    @Test
    void parse_whenTitleBlank_reportsRowError() {

        // given
        InputStream csv = toStream("""
            Track URI,Track Name,Artist Name(s)
            spotify:track:4uLU6hMCjMI75M1A2tKUQC,,Marc Anthony
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.tracks()).isEmpty();
        assertThat(result.errors()).singleElement()
            .satisfies(error -> assertThat(error.reason()).contains("tytułu"));
    }

    @Test
    void parse_whenRequiredColumnMissing_throwsValidationException() {

        // given
        InputStream csv = toStream("""
            Track Name,Artist Name(s)
            Vivir Mi Vida,Marc Anthony
            """);

        // when
        Throwable thrown = catchThrowable(() -> parser.parse(csv));

        // then
        assertThat(thrown).isInstanceOf(ValidationException.class)
            .hasMessageContaining("Spotify URI");
    }

    @Test
    void parse_whenHeaderHasBomAndDifferentCase_resolvesColumns() {

        // given
        InputStream csv = toStream("""
            ﻿TRACK URI,TRACK NAME,ARTIST NAME(S)
            spotify:track:4uLU6hMCjMI75M1A2tKUQC,Vivir Mi Vida,Marc Anthony
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.tracks()).hasSize(1);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void parse_whenAnalyzerExportHeaders_readsSongAndBareTrackId() {

        // given — eksport z analizatora playlist: „Song" zamiast „Track Name",
        // identyfikator gołym Track Id zamiast URI
        InputStream csv = toStream("""
            #,Song,Artist,BPM,Album,Spotify Track Id,ISRC
            1,La Lámpara,Alain Pérez,96,La Lámpara,2c7nzxJYmPtkimDdrhcfJx,ES71G2337397
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.tracks()).containsExactly(
            new ParsedTrack("2c7nzxJYmPtkimDdrhcfJx", "La Lámpara", "Alain Pérez", "La Lámpara"));
    }

    @Test
    void parse_whenQuoteInsideFieldNotEscaped_readsRowsInsteadOfFailingWholeFile() {

        // given — nieescapowany cudzysłów w nazwie wytwórni przerywał odczyt pliku
        InputStream csv = toStream("""
            Track URI,Track Name,Artist Name(s),Label
            spotify:track:4uLU6hMCjMI75M1A2tKUQC,Pa' Que Me Perdones,Héctor Acosta,"Héctor Acosta "El Torito""
            spotify:track:1zHlj4dQ8ZAtrayhuDDmkY,Danza Kuduro,Don Omar,Yanis
            """);

        // when
        CsvParseResult result = parser.parse(csv);

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.tracks()).extracting(ParsedTrack::spotifyId)
            .containsExactly("4uLU6hMCjMI75M1A2tKUQC", "1zHlj4dQ8ZAtrayhuDDmkY");
    }

    private InputStream toStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
