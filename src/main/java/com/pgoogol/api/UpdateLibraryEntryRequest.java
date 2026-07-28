package com.pgoogol.api;

import java.util.List;

/**
 * PATCH danych prywatnych DJ-a: {@code null} = bez zmian; pusty string /
 * pusta lista / rating 0 = wyczyszczenie pola.
 */
public record UpdateLibraryEntryRequest(
    String djNotes,
    List<String> customTags,
    Integer rating,
    String djSlotOverride) {

}
