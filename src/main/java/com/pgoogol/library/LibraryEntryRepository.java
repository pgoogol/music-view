package com.pgoogol.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

    Optional<LibraryEntry> findByTrackSpotifyId(String spotifyId);

    boolean existsByTrackSpotifyId(String spotifyId);
}
