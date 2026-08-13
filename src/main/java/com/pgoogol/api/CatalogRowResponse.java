package com.pgoogol.api;

import java.time.Instant;
import java.util.List;

/**
 * Wiersz wyszukiwarki katalogu (M5.6): dane deterministyczne utworu i — o ile
 * DJ ma go u siebie — jego dane prywatne. Rozdział z D3 obowiązuje też
 * w kontrakcie, więc to dwa obiekty, a nie jeden płaski rekord: {@code library}
 * puste znaczy „utwór jest w katalogu, ale nie w bibliotece", a nie „bez oceny".
 */
public record CatalogRowResponse(TrackResponse track, TrackLibraryResponse library) {

    /** Dane prywatne DJ-a pokazywane w tabeli — reszta wpisu jest w szufladzie. */
    public record TrackLibraryResponse(
        Integer rating,
        List<String> customTags,
        Instant addedAt,
        String source) {

    }
}
