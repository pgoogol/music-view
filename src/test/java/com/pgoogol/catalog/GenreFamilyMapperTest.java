package com.pgoogol.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprowadzanie swobodnych list gatunków do enuma D8 — reguły, które muszą
 * wygrywać z ogólniejszymi (disco polo, latynoskie dance).
 */
class GenreFamilyMapperTest {

    private final GenreFamilyMapper mapper = new GenreFamilyMapper();

    @ParameterizedTest
    @CsvSource({
        "'timba, son cubano, salsa',LATIN",
        "'salsa, merengue',LATIN",
        "'afrokubański jazz, latynoski jazz',LATIN",
        "'disco polo',DISCO_POLO",
        "'italo disco',DISCO",
        "'deep house, techno',ELECTRONIC",
        "'hip hop, trap',HIP_HOP",
        "'classic rock',ROCK",
        "'dance pop',POP",
    })
    void map_whenGenreListGiven_picksFamily(String genres, GenreFamily expected) {

        // when + then
        assertThat(mapper.map(List.of(genres))).contains(expected);
    }

    @Test
    void map_whenLatinDance_prefersLatinOverElectronic() {

        // given — „latynoskie dance" to salsa na parkiecie, nie muzyka klubowa

        // when + then
        assertThat(mapper.map(List.of("latynoskie rytmy, latynoski dance"))).contains(GenreFamily.LATIN);
    }

    @Test
    void map_whenDetailedGenresEmpty_fallsBackToParentGenres() {

        // when + then
        assertThat(mapper.map(List.of("", "Latin, Traditional Music"))).contains(GenreFamily.LATIN);
    }

    @Test
    void map_whenNothingRecognizable_returnsEmptyForLlm() {

        // when + then
        assertThat(mapper.map(List.of("", ""))).isEmpty();
        assertThat(mapper.map(List.of("Traditional Music"))).isEmpty();
        assertThat(mapper.map(List.of())).isEmpty();
    }
}
