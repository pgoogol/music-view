package com.pgoogol.library;

import com.pgoogol.catalog.CamelotKey;
import com.pgoogol.library.LibraryOverview.Bucket;
import com.pgoogol.library.LibraryOverview.MatrixCell;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rozkłady przeglądu biblioteki (M4.3, rozszerzone w M5.4) liczone w bazie
 * (D27). Jedenaście wymiarów schodzi jednym zapytaniem z {@code union all}
 * i etykietą wymiaru — jedenaście osobnych {@code group by} czytałoby tę samą
 * tabelę jedenaście razy.
 *
 * <p>Każdy wymiar podaje własny {@code sort_key}, bo naturalna kolejność
 * kategorii bywa inna niż alfabetyczna („SLOW" przed „MEDIUM"); {@code null}
 * zostaje osobną kategorią („bez wartości") — brak danych jest tu informacją,
 * a nie czymś do ukrycia.</p>
 */
@Repository
public class LibraryDistributionsRepository {

    private static final String NO_KEY = "BEZ TONACJI";

    /**
     * Rozkłady w jednym przebiegu. Wymiar {@code key} wraca surową tonacją
     * z katalogu — na koło Camelot przelicza ją {@link CamelotKey} w Javie
     * (D25 zabrania kolumny z pozycją koła), a zapisy enharmoniczne
     * („Eb minor" i „D# minor") schodzą się dopiero po tym przeliczeniu.
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
        select 'confidence', coalesce(confidence, 'BEZ ANALIZY'), count(*),
               case lower(confidence) when 'high' then '1' when 'medium' then '2'
                                      when 'low' then '3' else 'z' end
          from track_catalog group by confidence
        union all
        select 'decade', coalesce((((year / 10) * 10)::text || 's'), 'BEZ ROKU'), count(*),
               coalesce(lpad(((year / 10) * 10)::text, 6, '0'), 'zzzzzz')
          from track_catalog group by (year / 10) * 10
        union all
        select 'key', coalesce(upper(musical_key), 'BEZ TONACJI'), count(*),
               coalesce(upper(musical_key), 'zzz')
          from track_catalog group by upper(musical_key)
        union all
        select 'duration', coalesce(((duration_ms / 60000)::text || ' min'), 'BEZ CZASU'),
               count(*), coalesce(lpad((duration_ms / 60000)::text, 3, '0'), 'zzz')
          from track_catalog group by duration_ms / 60000
        union all
        select 'popularity',
               coalesce(((popularity / 10) * 10)::text || '–' || ((popularity / 10) * 10 + 9)::text,
                        'BEZ POPULARNOŚCI'),
               count(*), coalesce(lpad(((popularity / 10) * 10)::text, 3, '0'), 'zzz')
          from track_catalog group by (popularity / 10) * 10
        union all
        select 'rating', coalesce(rating::text, 'bez oceny'), count(*),
               coalesce(rating::text, 'z')
          from library_entry group by rating
        union all
        select 'source', coalesce(source, 'nieznane'), count(*), coalesce(source, 'zzz')
          from library_entry group by source
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

    private static final String MONTHLY_GROWTH = """
        select to_char(date_trunc('month', added_at), 'YYYY-MM') as label, count(*) as total
          from library_entry
         group by 1 order by 1 desc limit ?
        """;

    /** Tempo × energia — dwa wymiary naraz, więc poza wspólny {@code union all}. */
    private static final String TEMPO_ENERGY = """
        select coalesce(tempo_class, 'BEZ TEMPA') as tempo_class,
               coalesce(upper(energy), 'BEZ ENERGII') as energy,
               count(*) as total
          from track_catalog group by 1, 2
        """;

    private final JdbcTemplate jdbcTemplate;

    public LibraryDistributionsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Wszystkie rozkłady spod wspólnego zapytania, kluczowane nazwą wymiaru. */
    public Map<String, List<Bucket>> byDimension() {

        record Row(String dimension, Bucket bucket) { }
        List<Row> rows = jdbcTemplate.query(DISTRIBUTIONS, (resultSet, index) -> new Row(
            resultSet.getString("dimension"),
            new Bucket(resultSet.getString("label"), resultSet.getLong("total"))));
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(
            Row::dimension,
            LinkedHashMap::new,
            java.util.stream.Collectors.mapping(Row::bucket, java.util.stream.Collectors.toList())));
    }

    public List<Bucket> bpmHistogram() {

        return jdbcTemplate.query(BPM_HISTOGRAM, (resultSet, index) -> {
            int bucket = resultSet.getInt("bucket");
            return new Bucket("%d–%d".formatted(bucket, bucket + 9), resultSet.getLong("total"));
        });
    }

    /** Zapytanie bierze ostatnie miesiące, wykres czyta się od najstarszego. */
    public List<Bucket> growthOldestFirst(int months) {

        List<Bucket> recent = jdbcTemplate.query(MONTHLY_GROWTH, this::bucket, months);
        return recent.reversed();
    }

    public List<MatrixCell> tempoEnergy() {

        return jdbcTemplate.query(TEMPO_ENERGY, (resultSet, index) -> new MatrixCell(
            resultSet.getString("tempo_class"),
            resultSet.getString("energy"),
            resultSet.getLong("total")));
    }

    /**
     * Surowe tonacje z katalogu przeliczone na pozycje koła Camelot i zsumowane
     * — „Eb minor" i „D# minor" to ta sama pozycja 2A. Kolejność jest kolejnością
     * koła (1A, 1B, 2A…), bo front rysuje z tego tarczę, a nie listę.
     *
     * <p>Koszyk zastępczy dla {@code null}-a musi ominąć parser: „BEZ TONACJI"
     * zaczyna się od nazwy dźwięku, więc {@link CamelotKey} przeczytałby go jako
     * B-dur i 380 utworów bez tonacji wylądowałoby na pozycji 1B.</p>
     */
    public List<Bucket> camelotKeys(List<Bucket> rawKeys) {

        Map<String, Long> byLabel = new LinkedHashMap<>();
        rawKeys.forEach(bucket -> {
            Optional<CamelotKey> key = NO_KEY.equals(bucket.label())
                ? Optional.empty()
                : CamelotKey.ofMusicalKey(bucket.label());
            String label = key.map(CamelotKey::label).orElse(NO_KEY);
            byLabel.merge(label, bucket.count(), Long::sum);
        });
        return byLabel.entrySet().stream()
            .map(entry -> new Bucket(entry.getKey(), entry.getValue()))
            .sorted(java.util.Comparator.comparing(bucket -> wheelOrder(bucket.label())))
            .toList();
    }

    /** „10A" ma stać za „9A", więc numer idzie do klucza sortowania z wiodącym zerem. */
    private String wheelOrder(String label) {

        return CamelotKey.ofLabel(label)
            .map(key -> "%02d%s".formatted(key.number(), key.minor() ? "A" : "B"))
            .orElse("zz");
    }

    private Bucket bucket(ResultSet resultSet, int index) throws SQLException {
        return new Bucket(resultSet.getString("label"), resultSet.getLong("total"));
    }
}
