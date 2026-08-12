package com.pgoogol.library;

import com.pgoogol.catalog.CatalogSearchCriteria;
import com.pgoogol.catalog.CatalogService;
import com.pgoogol.catalog.CatalogSortOrder;
import com.pgoogol.catalog.TrackCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wyszukiwarka ekranu Biblioteka (M5.6): wynik z katalogu (D3) uzupełniony
 * o dane prywatne DJ-a, żeby ocena i tagi dały się pokazać i posortować
 * w tabeli, a nie dopiero po otwarciu szuflady utworu.
 *
 * <p>Dociągnięcie idzie osobnym zapytaniem po stronie ID-ków jednej strony
 * wyniku, a nie kolejnym złączeniem w projekcji: wyszukiwarka i tak już złącza
 * {@code library_entry} na potrzeby filtrów i sortowania, a mapowanie natywnego
 * zapytania na dwie encje naraz kosztowałoby ręczne przepisywanie kolumn
 * katalogu — dokładnie to, czego {@code select t.*} pozwala uniknąć.</p>
 */
@Service
public class LibrarySearchService {

    private final CatalogService catalogService;
    private final LibraryEntryRepository libraryEntryRepository;

    public LibrarySearchService(CatalogService catalogService,
                                LibraryEntryRepository libraryEntryRepository) {

        this.catalogService = catalogService;
        this.libraryEntryRepository = libraryEntryRepository;
    }

    @Transactional(readOnly = true)
    public Page<LibraryRow> search(CatalogSearchCriteria criteria,
                                   CatalogSortOrder sortOrder,
                                   Pageable pageable) {

        Objects.requireNonNull(criteria, "criteria");
        Page<TrackCatalog> tracks = catalogService.search(criteria, sortOrder, pageable);
        Map<String, LibraryEntry> entries = entriesByTrackId(tracks.getContent());
        return tracks.map(track -> new LibraryRow(track, entries.get(track.getSpotifyId())));
    }

    private Map<String, LibraryEntry> entriesByTrackId(List<TrackCatalog> tracks) {

        if (tracks.isEmpty()) {
            return Map.of();
        }
        List<String> spotifyIds = tracks.stream().map(TrackCatalog::getSpotifyId).toList();
        return libraryEntryRepository.findWithTrackByTrackSpotifyIdIn(spotifyIds).stream()
            .collect(Collectors.toMap(
                entry -> entry.getTrack().getSpotifyId(), Function.identity()));
    }
}
