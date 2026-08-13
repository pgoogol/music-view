package com.pgoogol.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    @Query("select t from TrackCatalog t where " + AUDIO_MISSING)
    List<TrackCatalog> findAudioMissing();

    @Query("select t from TrackCatalog t where " + AI_MISSING)
    List<TrackCatalog> findAiMissing();

    /**
     * Braki we wszystkich trzech grupach pól (D11/D17) jednym przejściem po tabeli
     * (D27) — wcześniej były to trzy osobne {@code count}-y odpalane przy każdym
     * wejściu na zakładkę Wzbogacanie.
     */
    @Query(value = """
        select
          count(*) filter (where isrc is null or year is null or duration_ms is null)
            as metadata,
          count(*) filter (where bpm is null or musical_key is null
                              or danceability is null or tempo_class is null)
            as audio,
          count(*) filter (where style is null or genre_family is null
                              or lyrics_theme is null or description_pl is null
                              or energy is null)
            as ai
        from track_catalog
        """, nativeQuery = true)
    MissingCounts countMissingByGroup();

    /** Projekcja pod {@link #countMissingByGroup()} — trzy liczby, jeden wiersz. */
    interface MissingCounts {

        long getMetadata();

        long getAudio();

        long getAi();
    }

    /**
     * Ile utworów obejmie zlecenie o zakresie MISSING dla wybranych grup pól —
     * potrzebne do szacunku kosztu przed startem joba (D28). Warunki łączy OR,
     * bo job bierze utwór, któremu brakuje czegokolwiek z zaznaczonych grup.
     */
    @Query(value = """
        select count(*) from track_catalog
         where (cast(:metadata as boolean)
                and (isrc is null or year is null or duration_ms is null))
            or (cast(:audio as boolean)
                and (bpm is null or musical_key is null
                     or danceability is null or tempo_class is null))
            or (cast(:ai as boolean)
                and (style is null or genre_family is null or lyrics_theme is null
                     or description_pl is null or energy is null))
        """, nativeQuery = true)
    long countMissingForFields(@Param("metadata") boolean metadata,
                               @Param("audio") boolean audio,
                               @Param("ai") boolean ai);

    /**
     * Utwory opisane innym modelem albo inną wersją promptu niż bieżąca
     * konfiguracja (D28). Utwór nigdy nieopisany tu nie wchodzi — należy
     * do zakresu MISSING.
     */
    @Query(value = """
        select count(*) from track_catalog
         where enriched_at is not null
           and (model_used is distinct from cast(:model as text)
                or enrich_version is distinct from cast(:version as integer))
        """, nativeQuery = true)
    long countOutdated(@Param("model") String model, @Param("version") Integer version);

    @Query("select t.spotifyId from TrackCatalog t where t.spotifyId in :spotifyIds")
    Set<String> findExistingIds(@Param("spotifyIds") Collection<String> spotifyIds);

    /**
     * Dopasowanie po ISRC dla importu metryk (D24) — ISRC identyfikuje nagranie,
     * więc jeden kod może wskazać kilka wydań w katalogu. Wielkość liter bywa
     * różna w eksportach, stąd porównanie po {@code upper}.
     */
    @Query("select t from TrackCatalog t where upper(t.isrc) in :isrcs")
    List<TrackCatalog> findByIsrcInIgnoreCase(@Param("isrcs") Collection<String> isrcs);

    /**
     * Wyszukiwarka katalogu (M1.7): pełnotekstowo po search_vector (tsvector,
     * generowana kolumna z V1) + fuzzy pg_trgm po title/artist; filtry D5
     * uzupełnione o filtry biblioteczne (M3.2), harmoniczne (D25), metryk (D24)
     * oraz filtry utworu i jakości danych (M5.6). Domyślnie (sort = RELEVANCE)
     * przy zapytaniu tekstowym kolejność wg trafności; pozostałe porządki
     * wg {@link CatalogSort}. Sam tekst zapytania siedzi w {@link CatalogSearchSql};
     * kolejność parametrów jest tam kolejnością warunków.
     */
    @Query(value = CatalogSearchSql.SELECT_TRACKS,
        countQuery = CatalogSearchSql.COUNT_TRACKS,
        nativeQuery = true)
    Page<TrackCatalog> search(@Param("search") String search,
                              @Param("genreFamily") String genreFamily,
                              @Param("yearMin") Integer yearMin,
                              @Param("yearMax") Integer yearMax,
                              @Param("durationMinMs") Integer durationMinMs,
                              @Param("durationMaxMs") Integer durationMaxMs,
                              @Param("popularityMin") Integer popularityMin,
                              @Param("explicit") Boolean explicit,
                              @Param("bpmMin") Integer bpmMin,
                              @Param("bpmMax") Integer bpmMax,
                              @Param("tempoClass") String tempoClass,
                              @Param("energy") String energy,
                              @Param("musicalKeys") String musicalKeys,
                              @Param("inLibrary") Boolean inLibrary,
                              @Param("ratingMin") Integer ratingMin,
                              @Param("tag") String tag,
                              @Param("valenceMin") BigDecimal valenceMin,
                              @Param("valenceMax") BigDecimal valenceMax,
                              @Param("instrumentalMin") BigDecimal instrumentalMin,
                              @Param("livenessMax") BigDecimal livenessMax,
                              @Param("bpmSource") String bpmSource,
                              @Param("missing") String missing,
                              @Param("sort") String sort,
                              @Param("direction") String direction,
                              Pageable pageable);

    /**
     * Pokrycie metrykami (M4.1) — filtry z {@code manual_metrics} działają tylko
     * na tym podzbiorze, więc UI musi umieć powiedzieć „X z Y utworów ma metryki".
     */
    @Query(value = "select count(*) from manual_metrics", nativeQuery = true)
    long countWithMetrics();
}
