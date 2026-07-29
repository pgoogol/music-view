package com.pgoogol.ingestion;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rozpoznaje utwór w tym, co przychodzi w kolumnie CSV: URI aplikacji
 * ({@code spotify:track:…}), linku „udostępnij" albo samym 22-znakowym
 * identyfikatorze. Wspólne dla importu biblioteki (M1.2) i metryk (D24).
 */
@Component
public class SpotifyTrackIdParser {

    private static final Pattern TRACK_ID = Pattern.compile(
        "^(?:spotify:track:|https?://open\\.spotify\\.com/track/)?([0-9A-Za-z]{22})(?:\\?\\S*)?$");

    public Optional<String> parse(@Nullable String rawReference) {

        if (Objects.isNull(rawReference) || rawReference.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TRACK_ID.matcher(rawReference.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
