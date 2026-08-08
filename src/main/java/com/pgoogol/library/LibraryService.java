package com.pgoogol.library;

import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.ConflictException;
import com.pgoogol.common.NotFoundException;
import com.pgoogol.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Biblioteka DJ-a (M1.7): lista z katalogiem, ręczne dodanie utworu,
 * aktualizacja danych prywatnych (D3), usunięcie wpisu (katalog zostaje —
 * dane deterministyczne deduplikują koszt wzbogacania).
 */
@Service
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final LibraryEntryRepository libraryEntryRepository;
    private final TrackCatalogRepository trackCatalogRepository;
    private final LibraryOverviewRepository libraryOverviewRepository;

    public LibraryService(LibraryEntryRepository libraryEntryRepository,
                          TrackCatalogRepository trackCatalogRepository,
                          LibraryOverviewRepository libraryOverviewRepository) {

        this.libraryEntryRepository = libraryEntryRepository;
        this.trackCatalogRepository = trackCatalogRepository;
        this.libraryOverviewRepository = libraryOverviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<LibraryEntry> list(Pageable pageable) {
        return libraryEntryRepository.findPageWithTrack(pageable);
    }

    /**
     * Przegląd biblioteki (M4.3) — wszystkie rozkłady liczy baza (D27). Braki
     * per grupa pól bierzemy z tego samego zapytania co zakładka Wzbogacanie,
     * żeby obie liczby nigdy się nie rozjechały.
     */
    @Transactional(readOnly = true)
    public LibraryOverview overview() {

        TrackCatalogRepository.MissingCounts missing = trackCatalogRepository.countMissingByGroup();
        return libraryOverviewRepository.load(
            missing.getMetadata(), missing.getAudio(), missing.getAi());
    }

    @Transactional(readOnly = true)
    public LibraryEntry get(String spotifyId) {
        return requireEntry(spotifyId);
    }

    /** Custom tagi użyte w bibliotece — podpowiedzi filtra wyszukiwarki (M3.2). */
    @Transactional(readOnly = true)
    public List<String> listTags() {
        return libraryEntryRepository.findDistinctTags();
    }

    /** Ręczne dodanie utworu — jak import z pliku (source=FILE); szkielet katalogu gdy brak. */
    @Transactional
    public LibraryEntry add(String spotifyId, String title, String artist, String album) {

        Objects.requireNonNull(spotifyId, "spotifyId");
        if (libraryEntryRepository.existsByTrackSpotifyId(spotifyId)) {
            throw new ConflictException("LIBRARY_ENTRY_EXISTS",
                "Utwór '%s' jest już w bibliotece".formatted(spotifyId));
        }
        TrackCatalog track = trackCatalogRepository.findById(spotifyId)
            .orElseGet(() -> {
                TrackCatalog skeleton = new TrackCatalog(spotifyId, title, artist);
                skeleton.setAlbum(album);
                return trackCatalogRepository.save(skeleton);
            });
        LibraryEntry entry = libraryEntryRepository.save(new LibraryEntry(track, LibrarySource.FILE));
        log.info("Dodano utwór {} do biblioteki (wpis {})", spotifyId, entry.getId());
        return entry;
    }

    @Transactional
    public LibraryEntry update(String spotifyId, LibraryEntryUpdate update) {

        Objects.requireNonNull(update, "update");
        LibraryEntry entry = requireEntry(spotifyId);
        requireCurrentVersion(entry, update.expectedVersion());
        if (Objects.nonNull(update.djNotes())) {
            entry.setDjNotes(update.djNotes().isBlank() ? null : update.djNotes());
        }
        if (Objects.nonNull(update.customTags())) {
            entry.setCustomTags(update.customTags().isEmpty() ? null : List.copyOf(update.customTags()));
        }
        if (Objects.nonNull(update.rating())) {
            entry.setRating(normalizedRating(update.rating()));
        }
        if (Objects.nonNull(update.djSlotOverride())) {
            entry.setDjSlotOverride(update.djSlotOverride().isBlank() ? null : update.djSlotOverride());
        }
        return entry;
    }

    @Transactional
    public void delete(String spotifyId) {

        LibraryEntry entry = requireEntry(spotifyId);
        libraryEntryRepository.delete(entry);
        log.info("Usunięto utwór {} z biblioteki (katalog bez zmian)", spotifyId);
    }

    private LibraryEntry requireEntry(String spotifyId) {

        Objects.requireNonNull(spotifyId, "spotifyId");
        return libraryEntryRepository.findWithTrackByTrackSpotifyId(spotifyId)
            .orElseThrow(() -> new NotFoundException("LIBRARY_ENTRY_NOT_FOUND",
                "Utworu '%s' nie ma w bibliotece".formatted(spotifyId)));
    }

    /**
     * Nieświeży klient (D29): wersja z żądania nie zgadza się z tą w bazie, więc
     * PATCH pisałby po zmianie, której nadawca nie widział. Wyścig równoległych
     * transakcji łapie osobno {@code @Version} na encji.
     */
    private void requireCurrentVersion(LibraryEntry entry, int expectedVersion) {

        if (entry.getVersion() != expectedVersion) {
            throw new ConflictException("RESOURCE_MODIFIED",
                ("Wpis zmienił się w innym miejscu (wersja %d, przysłano %d) — "
                    + "odśwież i spróbuj ponownie")
                    .formatted(entry.getVersion(), expectedVersion));
        }
    }

    private Integer normalizedRating(int rating) {

        if (rating == 0) {
            return null;
        }
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new ValidationException("RATING_OUT_OF_RANGE",
                "Rating musi być w zakresie %d–%d (0 czyści ocenę)".formatted(MIN_RATING, MAX_RATING));
        }
        return rating;
    }
}
