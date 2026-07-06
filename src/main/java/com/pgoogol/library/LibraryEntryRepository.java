package com.pgoogol.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

    Optional<LibraryEntry> findByTrackSpotifyId(String spotifyId);

    /** Lista biblioteki z dociągniętym katalogiem (join — bez N+1). */
    @Query(value = "select e from LibraryEntry e join fetch e.track",
           countQuery = "select count(e) from LibraryEntry e")
    Page<LibraryEntry> findPageWithTrack(Pageable pageable);

    /** Wpis z dociągniętym katalogiem — encja opuszcza transakcję serwisu (open-in-view=false). */
    @Query("select e from LibraryEntry e join fetch e.track where e.track.spotifyId = :spotifyId")
    Optional<LibraryEntry> findWithTrackByTrackSpotifyId(@Param("spotifyId") String spotifyId);

    boolean existsByTrackSpotifyId(String spotifyId);

    @Query("select e.track.spotifyId from LibraryEntry e where e.track.spotifyId in :spotifyIds")
    Set<String> findExistingTrackIds(@Param("spotifyIds") Collection<String> spotifyIds);
}
