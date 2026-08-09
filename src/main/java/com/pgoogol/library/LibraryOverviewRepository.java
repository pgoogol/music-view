package com.pgoogol.library;

import com.pgoogol.library.LibraryOverview.Bucket;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Agregaty przeglądu biblioteki (M4.3) liczone w bazie (D27) — front nie
 * dostaje 2500 wierszy po to, żeby je zliczyć w przeglądarce.
 *
 * <p>Bez cache: kilkanaście agregatów na tej skali Postgres liczy w kilkanaście
 * milisekund, a cache dołożyłby pytanie o unieważnianie po każdym imporcie
 * i po każdym jobie wzbogacania.</p>
 *
 * <p>Rozkłady po różnych kolumnach schodzą jednym zapytaniem z {@code union all}
 * i etykietą wymiaru — pięć osobnych {@code group by} czytałoby tę samą tabelę
 * pięć razy.</p>
 */
@Repository
public class LibraryOverviewRepository {

    private static final int TOP_ARTISTS = 10;
    private static final int GROWTH_MONTHS = 12;

    private static final String COUNTS = """
        select
          (select count(*) from track_catalog)  as catalog_tracks,
          (select count(*) from library_entry)  as library_tracks,
          (select count(*) from manual_metrics) as tracks_with_metrics
        """;

    /**
     * Wymiary rozkładów w jednym przebiegu. {@code null} zostaje osobną
     * kategorią („bez wartości") — brak danych jest tu informacją, a nie
     * czymś do ukrycia.
     */
    private static final String DISTRIBUTIONS = """
        select 'genre' as dimension, coalesce(genre_family, 'BEZ GATUNKU') as label,
               count(*) as total, coalesce(genre_family, 'zzz') as sort_key
          from track_catalog group by genre_family
        union all
        select 'tempo', coalesce(tempo_class, 'BEZ TEMPA'), count(*),
               case tempo_class when 'SLOW' then '1' when 'MEDIUM' then '2'
                                when 'FAST' then '3' when 'VERY_FAST' then '4' else 'z' end
          from track_catalog group by tempo_class
        union all
        select 'energy', coalesce(energy, 'BEZ ENERGII'), count(*),
               case lower(energy) when 'low' then '1' when 'medium' then '2'
                                  when 'high' then '3' else 'z' end
          from track_catalog group by energy
        union all
        select 'bpmSource', coalesce(bpm_source, 'BRAK BPM'), count(*),
               coalesce(bpm_source, 'zzz')
          from track_catalog group by bpm_source
        union all
        select 'rating', coalesce(rating::text, 'bez oceny'), count(*),
               coalesce(rating::text, 'z')
          from library_entry group by rating
        order by 1, 4, 2
        """;

    /**
     * Histogram BPM w koszykach po 10. Grupujemy po {@code floor(bpm/10)},
     * bo etykieta ma być czytelna („120–129"), a nie numerem kubełka.
     */
    private static final String BPM_HISTOGRAM = """
        select (floor(bpm / 10) * 10)::int as bucket, count(*) as total
          from track_catalog where bpm is not null
         group by 1 order by 1
        """;

    private static final String TOP_ARTISTS_QUERY = """
        select coalesce(t.artist, 'nieznany wykonawca') as label, count(*) as total
          from library_entry e join track_catalog t on t.spotify_id = e.spotify_id
         group by 1 order by total desc, label asc limit ?
        """;

    private static final String MONTHLY_GROWTH = """
        select to_char(date_trunc('month', added_at), 'YYYY-MM') as label, count(*) as total
          from library_entry
         group by 1 order by 1 desc limit ?
        """;

    private final JdbcTemplate jdbcTemplate;

    public LibraryOverviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LibraryOverview load(long metadataMissing, long audioMissing, long aiMissing,
                                long lyricsMissing) {

        Map<String, Object> counts = jdbcTemplate.queryForMap(COUNTS);
        Map<String, List<Bucket>> distributions = distributions();
        return new LibraryOverview(
            number(counts.get("catalog_tracks")),
            number(counts.get("library_tracks")),
            number(counts.get("tracks_with_metrics")),
            metadataMissing,
            audioMissing,
            aiMissing,
            lyricsMissing,
            distributions.getOrDefault("genre", List.of()),
            distributions.getOrDefault("tempo", List.of()),
            distributions.getOrDefault("energy", List.of()),
            distributions.getOrDefault("bpmSource", List.of()),
            distributions.getOrDefault("rating", List.of()),
            bpmHistogram(),
            jdbcTemplate.query(TOP_ARTISTS_QUERY, this::bucket, TOP_ARTISTS),
            growthOldestFirst());
    }

    private Map<String, List<Bucket>> distributions() {

        record Row(String dimension, Bucket bucket) { }
        List<Row> rows = jdbcTemplate.query(DISTRIBUTIONS, (resultSet, index) -> new Row(
            resultSet.getString("dimension"),
            new Bucket(resultSet.getString("label"), resultSet.getLong("total"))));
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(
            Row::dimension,
            java.util.LinkedHashMap::new,
            java.util.stream.Collectors.mapping(Row::bucket, java.util.stream.Collectors.toList())));
    }

    private List<Bucket> bpmHistogram() {

        return jdbcTemplate.query(BPM_HISTOGRAM, (resultSet, index) -> {
            int bucket = resultSet.getInt("bucket");
            return new Bucket("%d–%d".formatted(bucket, bucket + 9), resultSet.getLong("total"));
        });
    }

    /** Zapytanie bierze ostatnie miesiące, wykres czyta się od najstarszego. */
    private List<Bucket> growthOldestFirst() {

        List<Bucket> months = jdbcTemplate.query(MONTHLY_GROWTH, this::bucket, GROWTH_MONTHS);
        return months.reversed();
    }

    private Bucket bucket(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {
        return new Bucket(resultSet.getString("label"), resultSet.getLong("total"));
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
