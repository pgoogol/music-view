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
     * Katalog z dołączoną biblioteką DJ-a (M3.2) i metrykami z pliku (M4.1).
     * {@code library_entry.spotify_id} jest UNIQUE (V1), a {@code manual_metrics.spotify_id}
     * to klucz główny (V5), więc żadne z lewych złączeń nie zwielokrotnia wierszy
     * katalogu — utwór bez wpisu dostaje po prostu NULL-e w kolumnach {@code l}/{@code m}.
     */
    String SEARCH_FROM = """
         from track_catalog t
         left join library_entry l on l.spotify_id = t.spotify_id
         left join manual_metrics m on m.spotify_id = t.spotify_id
        """;

    /**
     * Filtr harmoniczny (D25) przychodzi jako lista dopuszczalnych zapisów tonacji
     * sklejona znakiem {@code |} — zbiór liczy aplikacja z koła Camelot, więc
     * zapytanie zostaje przy jednym porównaniu i nie potrzebuje kolejnego złączenia.
     * Filtry metryk celowo odsiewają utwory bez metryk (NULL nie spełnia nierówności).
     */
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
          and (cast(:inLibrary as boolean) is null
               or (cast(:inLibrary as boolean) = true and l.id is not null)
               or (cast(:inLibrary as boolean) = false and l.id is null))
          and (cast(:ratingMin as integer) is null or l.rating >= cast(:ratingMin as integer))
          and (cast(:tag as text) is null or cast(:tag as text) = any(l.custom_tags))
          and (cast(:musicalKeys as text) is null
               or upper(t.musical_key) = any(string_to_array(cast(:musicalKeys as text), '|')))
          and (cast(:valenceMin as numeric) is null or m.valence >= cast(:valenceMin as numeric))
          and (cast(:valenceMax as numeric) is null or m.valence <= cast(:valenceMax as numeric))
          and (cast(:instrumentalMin as numeric) is null
               or m.instrumentalness >= cast(:instrumentalMin as numeric))
          and (cast(:livenessMax as numeric) is null or m.liveness <= cast(:livenessMax as numeric))
        """;

    /**
     * Energia jest tekstem (D11), więc sortujemy ją po rosnącej sile, nie alfabetycznie.
     * Spacje na brzegach są istotne: stała wchodzi w środek text blocku, a ten ucina
     * białe znaki na końcach linii — bez nich powstałoby „thencase".
     */
    String ENERGY_RANK =
        " case lower(t.energy) when 'low' then 1 when 'medium' then 2 when 'high' then 3 end ";

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
     * generowana kolumna z V1) + fuzzy pg_trgm po title/artist; filtry D5
     * uzupełnione o filtry biblioteczne (M3.2). Domyślnie (sort = RELEVANCE)
     * przy zapytaniu tekstowym kolejność wg trafności; pozostałe porządki
     * wg {@link CatalogSort} (M3.1).
     */
    @Query(value = "select t.* " + SEARCH_FROM + SEARCH_WHERE + SEARCH_ORDER,
        countQuery = "select count(*) " + SEARCH_FROM + SEARCH_WHERE,
        nativeQuery = true)
    Page<TrackCatalog> search(@Param("search") String search,
                              @Param("genreFamily") String genreFamily,
                              @Param("bpmMin") Integer bpmMin,
                              @Param("bpmMax") Integer bpmMax,
                              @Param("tempoClass") String tempoClass,
                              @Param("energy") String energy,
                              @Param("inLibrary") Boolean inLibrary,
                              @Param("ratingMin") Integer ratingMin,
                              @Param("tag") String tag,
                              @Param("musicalKeys") String musicalKeys,
                              @Param("valenceMin") BigDecimal valenceMin,
                              @Param("valenceMax") BigDecimal valenceMax,
                              @Param("instrumentalMin") BigDecimal instrumentalMin,
                              @Param("livenessMax") BigDecimal livenessMax,
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
