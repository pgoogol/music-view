package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyciąga identyfikator playlisty z tego, co DJ ma pod ręką (M2.1): linku
 * „udostępnij" ({@code https://open.spotify.com/playlist/…}, także z prefiksem
 * regionalnym i parametrem {@code ?si=}), URI aplikacji ({@code spotify:playlist:…})
 * albo samego identyfikatora.
 */
@Component
public class SpotifyPlaylistUrlParser {

    private static final Pattern PLAYLIST_REFERENCE =
        Pattern.compile("(?:spotify:playlist:|playlist[/:])([A-Za-z0-9]+)");
    private static final Pattern BARE_ID = Pattern.compile("[A-Za-z0-9]{10,64}");

    public String parsePlaylistId(String urlOrId) {

        Objects.requireNonNull(urlOrId, "urlOrId");
        String trimmed = urlOrId.trim();
        Matcher reference = PLAYLIST_REFERENCE.matcher(trimmed);
        if (reference.find()) {
            return reference.group(1);
        }
        if (BARE_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        throw new ValidationException("PLAYLIST_URL_INVALID",
            "Nie rozpoznano playlisty w '%s' — podaj link, URI spotify:playlist:… lub samo id"
                .formatted(trimmed));
    }
}
