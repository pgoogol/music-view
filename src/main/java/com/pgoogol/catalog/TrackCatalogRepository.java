package com.pgoogol.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    String SEARCH_WHERE = """
        where (cast(:search as text) is null
               or t.search_vector @@ plainto_tsquery('simple', cast(:search as text))
               or similarity(t.title, cast(:search as text)) > 0.3
               or similarity(t.artist, cast(:search as text)) > 0.3)
          and (cast(:genreFamily as text) is null or t.genre_family = cast(:genreFamily as text))
          and (cast(:bpmMin as integer) is null or t.bpm >= cast(:bpmMin as integer))
          and (cast(:bpmMax as integer) is null or t.bpm <= cast(:bpmMax as integer))
          and (cast(:tempoClass as text) is null or t.tempo_class = cast(:tempoClass as text))
          and (cast(:energy as text) is null or lower(t.energy) = lower(cast(:energy as text)))
        """;

    /** Energia jest tekstem (D11), więc sortujemy ją po rosnącej sile, nie alfabetycznie. */
    String ENERGY_RANK =
        "case lower(t.energy) when 'low' then 1 when 'medium' then 2 when 'high' then 3 end";

    /**
     * Sortowanie z białej listy {@link CatalogSort} (M3.1). Każda kolumna dostaje
     * parę wyrażeń CASE (rosnąco/malejąco) — nieaktywne dają NULL dla wszystkich
     * wierszy, więc porządek rozstrzyga dopiero wyrażenie wybrane parametrem.
     * {@code nulls last} trzyma braki (np. utwory bez BPM) na końcu w obie strony.
     */
    String SEARCH_ORDER = """
        order by
          case when cast(:sort as text) = 'RELEVANCE' and cast(:search as text) is not null
               then greatest(similarity(t.title, cast(:search as text)),
                             similarity(t.artist, cast(:search as text))) end desc nulls last,
          case when cast(:sort as text) = 'TITLE'
               and cast(:direction as text) = 'ASC' then lower(t.title) end asc nulls last,
          case when cast(:sort as text) = 'TITLE'
               and cast(:direction as text) = 'DESC' then lower(t.title) end desc nulls last,
          case when cast(:sort as text) = 'ARTIST'
               and cast(:direction as text) = 'ASC' then lower(t.artist) end asc nulls last,
          case when cast(:sort as text) = 'ARTIST'
               and cast(:direction as text) = 'DESC' then lower(t.artist) end desc nulls last,
          case when cast(:sort as text) = 'YEAR'
               and cast(:direction as text) = 'ASC' then t.year end asc nulls last,
          case when cast(:sort as text) = 'YEAR'
               and cast(:direction as text) = 'DESC' then t.year end desc nulls last,
          case when cast(:sort as text) = 'BPM'
               and cast(:direction as text) = 'ASC' then t.bpm end asc nulls last,
          case when cast(:sort as text) = 'BPM'
               and cast(:direction as text) = 'DESC' then t.bpm end desc nulls last,
          case when cast(:sort as text) = 'POPULARITY'
               and cast(:direction as text) = 'ASC' then t.popularity end asc nulls last,
          case when cast(:sort as text) = 'POPULARITY'
               and cast(:direction as text) = 'DESC' then t.popularity end desc nulls last,
          case when cast(:sort as text) = 'DURATION'
               and cast(:direction as text) = 'ASC' then t.duration_ms end asc nulls last,
          case when cast(:sort as text) = 'DURATION'
               and cast(:direction as text) = 'DESC' then t.duration_ms end desc nulls last,
          case when cast(:sort as text) = 'ENERGY'
               and cast(:direction as text) = 'ASC' then """ + ENERGY_RANK + """
               end asc nulls last,
          case when cast(:sort as text) = 'ENERGY'
               and cast(:direction as text) = 'DESC' then """ + ENERGY_RANK + """
               end desc nulls last,
          t.artist, t.title, t.spotify_id
        """;

    /**
     * Wyszukiwarka katalogu (M1.7): pełnotekstowo po search_vector (tsvector,
     * generowana kolumna z V1) + fuzzy pg_trgm po title/artist; filtry D5.
     * Domyślnie (sort = RELEVANCE) przy zapytaniu tekstowym kolejność wg trafności;
     * pozostałe porządki wg {@link CatalogSort} (M3.1).
     */
    @Query(value = "select t.* from track_catalog t " + SEARCH_WHERE + SEARCH_ORDER,
        countQuery = "select count(*) from track_catalog t " + SEARCH_WHERE,
        nativeQuery = true)
    Page<TrackCatalog> search(@Param("search") String search,
                              @Param("genreFamily") String genreFamily,
                              @Param("bpmMin") Integer bpmMin,
                              @Param("bpmMax") Integer bpmMax,
                              @Param("tempoClass") String tempoClass,
                              @Param("energy") String energy,
                              @Param("sort") String sort,
                              @Param("direction") String direction,
                              Pageable pageable);
}
