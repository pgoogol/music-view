package com.pgoogol.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    /**
     * Słownik custom tagów użytych w bibliotece (M3.2) — pod podpowiedzi filtra.
     * Tagi siedzą w kolumnie {@code text[]}, więc rozwija je {@code unnest};
     * zapytanie natywne, bo JPQL nie zna tablic Postgresa.
     */
    @Query(value = """
        select distinct trim(tag) from library_entry e, unnest(e.custom_tags) as tag
        where trim(tag) <> '' order by 1
        """, nativeQuery = true)
    List<String> findDistinctTags();

    /** Same override'y slotów dla podanych utworów — pod planowanie setu (M2.3). */
    @Query("""
        select new com.pgoogol.library.TrackSlotOverride(e.track.spotifyId, e.djSlotOverride)
        from LibraryEntry e
        where e.track.spotifyId in :spotifyIds and e.djSlotOverride is not null
        """)
    List<TrackSlotOverride> findSlotOverrides(@Param("spotifyIds") Collection<String> spotifyIds);
}
