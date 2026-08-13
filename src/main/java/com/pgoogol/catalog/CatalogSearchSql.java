package com.pgoogol.catalog;

/**
 * SQL wyszukiwarki katalogu (M1.7, komplet filtrów i porządków w M5.6) trzymany
 * osobno od {@link TrackCatalogRepository}: po dołożeniu filtrów utworu, jakości
 * danych i sortowania po danych DJ-a sam tekst zapytania jest dłuższy niż cała
 * reszta repozytorium.
 *
 * <p>Każdy filtr ma ten sam kształt — NULL w parametrze znaczy „bez warunku".
 * Dzięki temu jedno zapytanie obsługuje dowolną kombinację zaznaczeń, a plan
 * zapytania nie zależy od tego, co DJ akurat wpisał w panel filtrów.</p>
 */
final class CatalogSearchSql {

    private CatalogSearchSql() {

    }

    /** Warunki „braku danych" (D11/D17) w wariancie SQL — z aliasem wyszukiwarki. */
    private static final String METADATA_MISSING =
        "t.isrc is null or t.year is null or t.duration_ms is null";
    private static final String AUDIO_MISSING =
        "t.bpm is null or t.musical_key is null or t.danceability is null or t.tempo_class is null";
    private static final String AI_MISSING =
        "t.style is null or t.genre_family is null or t.lyrics_theme is null "
            + "or t.description_pl is null or t.energy is null";

    /**
     * Katalog z dołączoną biblioteką DJ-a (M3.2) i metrykami z pliku (M4.1).
     * {@code library_entry.spotify_id} jest UNIQUE (V1), a {@code manual_metrics.spotify_id}
     * to klucz główny (V5), więc żadne z lewych złączeń nie zwielokrotnia wierszy
     * katalogu — utwór bez wpisu dostaje po prostu NULL-e w kolumnach {@code l}/{@code m}.
     */
    static final String FROM = """
         from track_catalog t
         left join library_entry l on l.spotify_id = t.spotify_id
         left join manual_metrics m on m.spotify_id = t.spotify_id
        """;

    /** Pełny tekst + fuzzy po tytule i wykonawcy (M1.7). */
    private static final String SEARCH_CLAUSE = """
        where (cast(:search as text) is null
               or t.search_vector @@ plainto_tsquery('simple', cast(:search as text))
               or similarity(t.title, cast(:search as text)) > 0.3
               or similarity(t.artist, cast(:search as text)) > 0.3)
        """;

    /** Filtry samego utworu: gatunek, rocznik, długość, popularność, explicit. */
    private static final String TRACK_CLAUSE = """
          and (cast(:genreFamily as text) is null or t.genre_family = cast(:genreFamily as text))
          and (cast(:yearMin as integer) is null or t.year >= cast(:yearMin as integer))
          and (cast(:yearMax as integer) is null or t.year <= cast(:yearMax as integer))
          and (cast(:durationMinMs as integer) is null
               or t.duration_ms >= cast(:durationMinMs as integer))
          and (cast(:durationMaxMs as integer) is null
               or t.duration_ms <= cast(:durationMaxMs as integer))
          and (cast(:popularityMin as integer) is null
               or t.popularity >= cast(:popularityMin as integer))
          and (cast(:explicit as boolean) is null or t.explicit = cast(:explicit as boolean))
        """;

    /**
     * Filtry brzmienia. Filtr harmoniczny (D25) przychodzi jako lista dopuszczalnych
     * zapisów tonacji sklejona znakiem {@code |} — zbiór liczy aplikacja z koła
     * Camelot, więc zapytanie zostaje przy jednym porównaniu i nie potrzebuje
     * kolejnego złączenia.
     */
    private static final String SOUND_CLAUSE = """
          and (cast(:bpmMin as integer) is null or t.bpm >= cast(:bpmMin as integer))
          and (cast(:bpmMax as integer) is null or t.bpm <= cast(:bpmMax as integer))
          and (cast(:tempoClass as text) is null or t.tempo_class = cast(:tempoClass as text))
          and (cast(:energy as text) is null or lower(t.energy) = lower(cast(:energy as text)))
          and (cast(:musicalKeys as text) is null
               or upper(t.musical_key) = any(string_to_array(cast(:musicalKeys as text), '|')))
        """;

    /** Filtry po danych prywatnych DJ-a (D3) — wyszukiwarka nadal chodzi po katalogu. */
    private static final String LIBRARY_CLAUSE = """
          and (cast(:inLibrary as boolean) is null
               or (cast(:inLibrary as boolean) = true and l.id is not null)
               or (cast(:inLibrary as boolean) = false and l.id is null))
          and (cast(:ratingMin as integer) is null or l.rating >= cast(:ratingMin as integer))
          and (cast(:tag as text) is null or cast(:tag as text) = any(l.custom_tags))
        """;

    /**
     * Filtry metryk z pliku (D24) celowo odsiewają utwory bez metryk — NULL nie
     * spełnia nierówności. UI musi to mówić wprost licznikiem pokrycia, inaczej
     * pusty wynik wygląda jak awaria.
     */
    private static final String METRICS_CLAUSE = """
          and (cast(:valenceMin as numeric) is null or m.valence >= cast(:valenceMin as numeric))
          and (cast(:valenceMax as numeric) is null or m.valence <= cast(:valenceMax as numeric))
          and (cast(:instrumentalMin as numeric) is null
               or m.instrumentalness >= cast(:instrumentalMin as numeric))
          and (cast(:livenessMax as numeric) is null or m.liveness <= cast(:livenessMax as numeric))
        """;

    /**
     * Filtry jakości danych (M5.6): skąd wzięło się tempo (kryterium D19) i czego
     * utworowi brakuje. {@code ANY} to „cokolwiek z trzech grup" — to samo pytanie,
     * które zadaje zakładka Wzbogacanie, tyle że zadane z poziomu biblioteki.
     */
    private static final String QUALITY_CLAUSE =
        "  and (cast(:bpmSource as text) is null or t.bpm_source = cast(:bpmSource as text))\n"
            + "  and (cast(:missing as text) is null\n"
            + "       or (cast(:missing as text) in ('METADATA', 'ANY')\n"
            + "           and (" + METADATA_MISSING + "))\n"
            + "       or (cast(:missing as text) in ('AUDIO', 'ANY')\n"
            + "           and (" + AUDIO_MISSING + "))\n"
            + "       or (cast(:missing as text) in ('AI', 'ANY')\n"
            + "           and (" + AI_MISSING + ")))\n";

    static final String WHERE =
        SEARCH_CLAUSE + TRACK_CLAUSE + SOUND_CLAUSE + LIBRARY_CLAUSE + METRICS_CLAUSE
            + QUALITY_CLAUSE;

    /**
     * Energia jest tekstem (D11), więc sortujemy ją po rosnącej sile, nie alfabetycznie.
     * Spacje na brzegach są istotne: stała wchodzi w środek text blocku, a ten ucina
     * białe znaki na końcach linii — bez nich powstałoby „thencase".
     */
    private static final String ENERGY_RANK =
        " case lower(t.energy) when 'low' then 1 when 'medium' then 2 when 'high' then 3 end ";

    /**
     * Sortowanie z białej listy {@link CatalogSort} (M3.1, porządki po danych DJ-a
     * w M5.6). Każda kolumna dostaje parę wyrażeń CASE (rosnąco/malejąco) —
     * nieaktywne dają NULL dla wszystkich wierszy, więc porządek rozstrzyga dopiero
     * wyrażenie wybrane parametrem. {@code nulls last} trzyma braki (np. utwory bez
     * BPM albo nieocenione) na końcu w obie strony.
     */
    private static final String ORDER_HEAD = """
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
          case when cast(:sort as text) = 'ALBUM'
               and cast(:direction as text) = 'ASC' then lower(t.album) end asc nulls last,
          case when cast(:sort as text) = 'ALBUM'
               and cast(:direction as text) = 'DESC' then lower(t.album) end desc nulls last,
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
          case when cast(:sort as text) = 'DANCEABILITY'
               and cast(:direction as text) = 'ASC' then t.danceability end asc nulls last,
          case when cast(:sort as text) = 'DANCEABILITY'
               and cast(:direction as text) = 'DESC' then t.danceability end desc nulls last,
        """;

    /** Porządki po danych prywatnych DJ-a (D3) — kolumny ze złączonego wpisu. */
    private static final String ORDER_TAIL = """
          case when cast(:sort as text) = 'RATING'
               and cast(:direction as text) = 'ASC' then l.rating end asc nulls last,
          case when cast(:sort as text) = 'RATING'
               and cast(:direction as text) = 'DESC' then l.rating end desc nulls last,
          case when cast(:sort as text) = 'ADDED_AT'
               and cast(:direction as text) = 'ASC' then l.added_at end asc nulls last,
          case when cast(:sort as text) = 'ADDED_AT'
               and cast(:direction as text) = 'DESC' then l.added_at end desc nulls last,
          t.artist, t.title, t.spotify_id
        """;

    static final String ORDER =
        ORDER_HEAD
            + "  case when cast(:sort as text) = 'ENERGY'\n"
            + "       and cast(:direction as text) = 'ASC' then" + ENERGY_RANK
            + "end asc nulls last,\n"
            + "  case when cast(:sort as text) = 'ENERGY'\n"
            + "       and cast(:direction as text) = 'DESC' then" + ENERGY_RANK
            + "end desc nulls last,\n"
            + ORDER_TAIL;

    static final String SELECT_TRACKS = "select t.* " + FROM + WHERE + ORDER;

    static final String COUNT_TRACKS = "select count(*) " + FROM + WHERE;
}
