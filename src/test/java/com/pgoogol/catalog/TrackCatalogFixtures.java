package com.pgoogol.catalog;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Object Mother dla {@link TrackCatalog} (docs/rules/testing.md).
 */
public final class TrackCatalogFixtures {

    private TrackCatalogFixtures() {

    }

    /** Szkielet rekordu jak po imporcie CSV (M1.2) — tylko id + tytuł + artysta. */
    public static TrackCatalog skeletonTrack(String spotifyId) {
        return new TrackCatalog(spotifyId, "La Vida Es Un Carnaval", "Celia Cruz");
    }

    /** Rekord z kompletem pól D5 — jak po pełnym wzbogaceniu METADATA+AUDIO+AI. */
    public static TrackCatalog enrichedTrack(String spotifyId) {

        TrackCatalog track = new TrackCatalog(spotifyId, "Vivir Mi Vida", "Marc Anthony");
        track.setAlbum("3.0");
        track.setIsrc("USSD11300483");
        track.setYear(2013);
        track.setDurationMs(252306);
        track.setPopularity(80);
        track.setExplicit(false);
        track.setAlbumImageUrl("https://i.scdn.co/image/example");
        track.setGenreFamily(GenreFamily.LATIN);
        track.setStyle("salsa");
        track.setBpm(92);
        track.setBpmSource(BpmSource.ACOUSTICBRAINZ);
        track.setDanceability(new BigDecimal("0.850"));
        track.setMusicalKey("A minor");
        track.setTempoClass(TempoClass.MEDIUM);
        track.setEnergy("high");
        track.setLyricsTheme("afirmacja życia po trudnych chwilach");
        track.setDescriptionPl("Energetyczna salsa o radości życia.");
        track.setConfidence("high");
        track.setEnrichedAt(Instant.parse("2026-07-03T12:00:00Z"));
        track.setModelUsed("test-model-1");
        track.setEnrichVersion(1);
        return track;
    }
}
