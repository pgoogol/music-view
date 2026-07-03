package com.pgoogol.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

    Optional<LibraryEntry> findByTrackSpotifyId(String spotifyId);

    boolean existsByTrackSpotifyId(String spotifyId);

    @Query("select e.track.spotifyId from LibraryEntry e where e.track.spotifyId in :spotifyIds")
    Set<String> findExistingTrackIds(@Param("spotifyIds") Collection<String> spotifyIds);
}
