package com.pgoogol.api;

import java.time.Instant;
import java.util.List;

/**
 * Wpis biblioteki + pełny rekord katalogu (join — M1.7).
 */
public record LibraryEntryResponse(
    Long id,
    String spotifyId,
    String source,
    Instant addedAt,
    String djNotes,
    List<String> customTags,
    Integer rating,
    String djSlotOverride,
    /** Wersja do blokady optymistycznej (D29) — odeślij ją przy PATCH-u. */
    int version,
    TrackResponse track) {

}
