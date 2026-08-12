package com.pgoogol.library;

import com.pgoogol.library.LibraryOverview.Bucket;
import com.pgoogol.library.LibraryOverview.Metric;
import com.pgoogol.library.LibraryOverview.RecentTrack;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Liczby i próbki przeglądu biblioteki (M4.3, rozszerzone w M5.4) liczone
 * w bazie (D27) — front nie dostaje 2500 wierszy po to, żeby je zliczyć
 * w przeglądarce. Rozkłady mieszkają obok, w {@link LibraryDistributionsRepository}.
 *
 * <p>Bez cache: kilkanaście agregatów na tej skali Postgres liczy w kilkanaście
 * milisekund, a cache dołożyłby pytanie o unieważnianie po każdym imporcie
 * i po każdym jobie wzbogacania.</p>
 */
@Repository
public class LibraryOverviewRepository {

    private static final int TOP_ARTISTS = 10;
    private static final int TOP_TAGS = 16;
    private static final int TOP_STYLES = 12;
    private static final int GROWTH_MONTHS = 12;
    private static final int RECENT_TRACKS = 12;

    /**
     * Liczby nagłówkowe jednym zapytaniem. Czas i wykonawców liczymy po
     * bibliotece, nie po katalogu: katalog trzyma też utwory, które DJ zna
     * ze wzbogacania, ale których u siebie nie ma.
     */
    private static final String COUNTS = """
        select
          (select count(*) from track_catalog)                     as catalog_tracks,
          (select count(*) from library_entry)                     as library_tracks,
          (select count(*) from manual_metrics)                    as tracks_with_metrics,
          (select count(*) from playlist)                          as playlists,
          (select count(distinct spotify_id) from playlist_track)  as tracks_in_playlists,
          (select coalesce(sum(t.duration_ms), 0)
             from library_entry e join track_catalog t on t.spotify_id = e.spotify_id)
                                                                   as library_duration_ms,
          (select count(distinct t.artist)
             from library_entry e join track_catalog t on t.spotify_id = e.spotify_id
            where t.artist is not null)                            as distinct_artists,
          (select avg(bpm) from track_catalog where bpm is not null) as average_bpm,
          (select count(*) from library_entry e
            where not exists (select 1 from playlist_track p
                               where p.spotify_id = e.spotify_id)) as tracks_outside_playlists
        """;

    private static final String TOP_ARTISTS_QUERY = """
        select coalesce(t.artist, 'nieznany wykonawca') as label, count(*) as total
          from library_entry e join track_catalog t on t.spotify_id = e.spotify_id
         group by 1 order by total desc, label asc limit ?
        """;

    /** Tagi są tablicą, więc rozwijamy ją bocznie; wpis bez tagów nie daje wiersza. */
    private static final String TOP_TAGS_QUERY = """
        select tag as label, count(*) as total
          from library_entry e
          cross join lateral unnest(coalesce(e.custom_tags, '{}'::text[])) as tag
         where tag <> ''
         group by 1 order by total desc, label asc limit ?
        """;

    private static final String TOP_STYLES_QUERY = """
        select style as label, count(*) as total
          from track_catalog
         where style is not null and style <> ''
         group by 1 order by total desc, label asc limit ?
        """;

    /**
     * Uśredniony profil brzmienia z metryk ręcznych (D24). Puste
     * {@code manual_metrics} daje same {@code null}-e i pusty profil —
     * średnia z zera utworów to nie zero, tylko brak odpowiedzi.
     */
    private static final String AUDIO_PROFILE = """
        select avg(danceability) as danceability, avg(energy) as energy,
               avg(valence) as valence, avg(acousticness) as acousticness,
               avg(instrumentalness) as instrumentalness, avg(speechiness) as speechiness,
               avg(liveness) as liveness
          from manual_metrics
        """;

    private static final String RECENTLY_ADDED = """
        select t.spotify_id, t.title, t.artist, t.album_image_url, e.added_at
          from library_entry e join track_catalog t on t.spotify_id = e.spotify_id
         order by e.added_at desc, e.id desc limit ?
        """;

    /** Kolejność osi pajęczyny; nazwy pól = kolumny {@link #AUDIO_PROFILE}. */
    private static final List<String> PROFILE_AXES = List.of(
        "danceability", "energy", "valence", "acousticness",
        "instrumentalness", "speechiness", "liveness");

    private final JdbcTemplate jdbcTemplate;
    private final LibraryDistributionsRepository distributionsRepository;

    public LibraryOverviewRepository(JdbcTemplate jdbcTemplate,
                                     LibraryDistributionsRepository distributionsRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.distributionsRepository = distributionsRepository;
    }

    public LibraryOverview load(long metadataMissing, long audioMissing, long aiMissing) {

        Map<String, Object> counts = jdbcTemplate.queryForMap(COUNTS);
        Map<String, List<Bucket>> distributions = distributionsRepository.byDimension();
        return new LibraryOverview(
            scale(counts),
            quality(metadataMissing, audioMissing, aiMissing, distributions),
            sound(distributions),
            timeline(distributions),
            taste(distributions),
            recentlyAdded());
    }

    private LibraryOverview.Scale scale(Map<String, Object> counts) {

        Number averageBpm = (Number) counts.get("average_bpm");
        return new LibraryOverview.Scale(
            number(counts.get("catalog_tracks")),
            number(counts.get("library_tracks")),
            number(counts.get("tracks_with_metrics")),
            number(counts.get("library_duration_ms")),
            number(counts.get("distinct_artists")),
            Objects.isNull(averageBpm) ? null : averageBpm.doubleValue(),
            number(counts.get("playlists")),
            number(counts.get("tracks_in_playlists")),
            number(counts.get("tracks_outside_playlists")));
    }

    private LibraryOverview.Quality quality(long metadataMissing, long audioMissing, long aiMissing,
                                            Map<String, List<Bucket>> distributions) {

        return new LibraryOverview.Quality(
            metadataMissing,
            audioMissing,
            aiMissing,
            dimension(distributions, "bpmSource"),
            dimension(distributions, "confidence"));
    }

    private LibraryOverview.Sound sound(Map<String, List<Bucket>> distributions) {

        return new LibraryOverview.Sound(
            dimension(distributions, "genre"),
            jdbcTemplate.query(TOP_STYLES_QUERY, this::bucket, TOP_STYLES),
            dimension(distributions, "tempo"),
            dimension(distributions, "energy"),
            distributionsRepository.bpmHistogram(),
            distributionsRepository.camelotKeys(dimension(distributions, "key")),
            dimension(distributions, "duration"),
            dimension(distributions, "popularity"),
            distributionsRepository.tempoEnergy(),
            audioProfile());
    }

    private LibraryOverview.Timeline timeline(Map<String, List<Bucket>> distributions) {

        return new LibraryOverview.Timeline(
            distributionsRepository.growthOldestFirst(GROWTH_MONTHS),
            dimension(distributions, "decade"));
    }

    private LibraryOverview.Taste taste(Map<String, List<Bucket>> distributions) {

        return new LibraryOverview.Taste(
            jdbcTemplate.query(TOP_ARTISTS_QUERY, this::bucket, TOP_ARTISTS),
            jdbcTemplate.query(TOP_TAGS_QUERY, this::bucket, TOP_TAGS),
            dimension(distributions, "rating"),
            dimension(distributions, "source"));
    }

    private List<Metric> audioProfile() {

        Map<String, Object> averages = jdbcTemplate.queryForMap(AUDIO_PROFILE);
        return PROFILE_AXES.stream()
            .filter(axis -> Objects.nonNull(averages.get(axis)))
            .map(axis -> new Metric(axis, ((Number) averages.get(axis)).doubleValue()))
            .toList();
    }

    private List<RecentTrack> recentlyAdded() {

        return jdbcTemplate.query(RECENTLY_ADDED, (resultSet, index) -> new RecentTrack(
            resultSet.getString("spotify_id"),
            resultSet.getString("title"),
            resultSet.getString("artist"),
            resultSet.getString("album_image_url"),
            resultSet.getTimestamp("added_at").toInstant()), RECENT_TRACKS);
    }

    private List<Bucket> dimension(Map<String, List<Bucket>> distributions, String name) {
        return distributions.getOrDefault(name, List.of());
    }

    private Bucket bucket(ResultSet resultSet, int index) throws SQLException {
        return new Bucket(resultSet.getString("label"), resultSet.getLong("total"));
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
