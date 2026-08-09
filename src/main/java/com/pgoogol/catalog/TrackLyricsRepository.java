package com.pgoogol.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Teksty utworów (D32). Braki liczy {@link TrackCatalogRepository} razem
 * z pozostałymi grupami pól — jednym przejściem po katalogu (D27).
 */
public interface TrackLyricsRepository extends JpaRepository<TrackLyrics, String> {

}
