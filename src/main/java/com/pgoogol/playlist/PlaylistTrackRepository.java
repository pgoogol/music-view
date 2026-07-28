package com.pgoogol.playlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {

    List<PlaylistTrack> findAllByPlaylistIdOrderByPositionAsc(Long playlistId);

    /** Lista utworów playlisty z dociągniętym katalogiem (join — bez N+1). */
    @Query("select pt from PlaylistTrack pt join fetch pt.track "
        + "where pt.playlist.id = :playlistId order by pt.position")
    List<PlaylistTrack> findAllWithTrackByPlaylistId(@Param("playlistId") Long playlistId);

    Optional<PlaylistTrack> findByPlaylistIdAndTrackSpotifyId(Long playlistId, String spotifyId);

    long countByPlaylistId(Long playlistId);

    /**
     * Czyszczenie playlisty przed odtworzeniem kolejności; DML omija kontekst
     * persystencji, dlatego {@code flushAutomatically} gwarantuje kolejność
     * delete→insert wobec unikalności (playlist_id, spotify_id) — D17.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PlaylistTrack pt where pt.playlist.id = :playlistId")
    void deleteByPlaylistId(@Param("playlistId") Long playlistId);
}
