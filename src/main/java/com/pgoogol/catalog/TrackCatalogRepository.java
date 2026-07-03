package com.pgoogol.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Zapytania „missing" per grupa pól (D11/D17) — pola-wyznaczniki braków:
 * METADATA → isrc/year/duration_ms, AUDIO → bpm/musical_key/danceability/tempo_class,
 * AI → style/genre_family/lyrics_theme/description_pl/energy. Mapowanie
 * {@code FieldGroup} → zapytanie robi warstwa serwisowa enrichmentu (M1.6).
 */
public interface TrackCatalogRepository extends JpaRepository<TrackCatalog, String> {

    String METADATA_MISSING = "t.isrc is null or t.year is null or t.durationMs is null";
    String AUDIO_MISSING =
        "t.bpm is null or t.musicalKey is null or t.danceability is null or t.tempoClass is null";
    String AI_MISSING = "t.style is null or t.genreFamily is null or t.lyricsTheme is null "
        + "or t.descriptionPl is null or t.energy is null";

    @Query("select t from TrackCatalog t where " + METADATA_MISSING)
    List<TrackCatalog> findMetadataMissing();

    @Query("select count(t) from TrackCatalog t where " + METADATA_MISSING)
    long countMetadataMissing();

    @Query("select t from TrackCatalog t where " + AUDIO_MISSING)
    List<TrackCatalog> findAudioMissing();

    @Query("select count(t) from TrackCatalog t where " + AUDIO_MISSING)
    long countAudioMissing();

    @Query("select t from TrackCatalog t where " + AI_MISSING)
    List<TrackCatalog> findAiMissing();

    @Query("select count(t) from TrackCatalog t where " + AI_MISSING)
    long countAiMissing();

    @Query("select t.spotifyId from TrackCatalog t where t.spotifyId in :spotifyIds")
    Set<String> findExistingIds(@Param("spotifyIds") Collection<String> spotifyIds);
}
