package com.pgoogol.playlist;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.catalog.TrackCatalog;
import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Utwór w puli generatora setu (M4.2) razem z tym, co generator o nim wie:
 * slot wieczoru (D9 — po uwzględnieniu override'u DJ-a) i ocena z biblioteki.
 * Pozycję koła Camelot (D25) liczymy raz przy budowie puli, żeby nie parsować
 * tonacji przy każdym porównaniu kandydatów.
 */
public record SetCandidate(TrackCatalog track, @Nullable DjSlot slot, @Nullable Integer rating) {

    public SetCandidate {
        Objects.requireNonNull(track, "track");
    }

    public String spotifyId() {
        return track.getSpotifyId();
    }

    public Optional<CamelotKey> key() {
        return CamelotKey.ofMusicalKey(track.getMusicalKey());
    }

    public Optional<Integer> bpm() {
        return Optional.ofNullable(track.getBpm());
    }

    /** Wykonawca w postaci porównywalnej — odstęp między utworami liczy się po nim. */
    public String artistKey() {
        return Objects.toString(track.getArtist(), "").trim().toLowerCase(java.util.Locale.ROOT);
    }
}
