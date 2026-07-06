package com.pgoogol.library;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Częściowa aktualizacja danych prywatnych DJ-a (PATCH, M1.7).
 * Semantyka: {@code null} = bez zmian; pusty string / pusta lista / rating 0
 * = wyczyszczenie pola.
 */
public record LibraryEntryUpdate(
    @Nullable String djNotes,
    @Nullable List<String> customTags,
    @Nullable Integer rating,
    @Nullable String djSlotOverride) {

}
