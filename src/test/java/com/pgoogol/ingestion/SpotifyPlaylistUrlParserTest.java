package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpotifyPlaylistUrlParserTest {

    private static final String PLAYLIST_ID = "37i9dQZF1DX10zKzsJ2jva";

    private final SpotifyPlaylistUrlParser parser = new SpotifyPlaylistUrlParser();

    @ParameterizedTest
    @ValueSource(strings = {
        "https://open.spotify.com/playlist/37i9dQZF1DX10zKzsJ2jva",
        "https://open.spotify.com/playlist/37i9dQZF1DX10zKzsJ2jva?si=6f0d0e6a1b2c4d3e",
        "https://open.spotify.com/intl-pl/playlist/37i9dQZF1DX10zKzsJ2jva",
        "spotify:playlist:37i9dQZF1DX10zKzsJ2jva",
        "  37i9dQZF1DX10zKzsJ2jva  ",
    })
    void parsePlaylistId_whenKnownReferenceForm_extractsId(String reference) {

        // when
        String playlistId = parser.parsePlaylistId(reference);

        // then
        assertThat(playlistId).isEqualTo(PLAYLIST_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy",
        "https://example.com/",
        "krótkie",
        "",
    })
    void parsePlaylistId_whenNotAPlaylistReference_throwsValidationException(String reference) {

        // when + then
        assertThatThrownBy(() -> parser.parsePlaylistId(reference))
            .isInstanceOf(ValidationException.class)
            .hasFieldOrPropertyWithValue("errorCode", "PLAYLIST_URL_INVALID");
    }
}
