package com.pgoogol.playlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Optional<Playlist> findBySpotifyPlaylistId(String spotifyPlaylistId);

    /** Lista playlist z liczbą utworów — jednym zapytaniem, bez dociągania utworów. */
    @Query("""
        select new com.pgoogol.playlist.PlaylistSummary(
            p.id, p.name, p.spotifyPlaylistId, p.createdAt, count(pt.id))
        from Playlist p
        left join PlaylistTrack pt on pt.playlist = p
        group by p.id, p.name, p.spotifyPlaylistId, p.createdAt
        order by p.createdAt desc
        """)
    List<PlaylistSummary> findAllSummaries();
}
