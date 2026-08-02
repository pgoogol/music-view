package com.pgoogol.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * PATCH danych prywatnych DJ-a: {@code null} = bez zmian; pusty string /
 * pusta lista / rating 0 = wyczyszczenie pola.
 *
 * <p>{@code version} jest wymagana (D29): cichy zapis „ostatni wygrywa" jest
 * gorszy od komunikatu, bo notatka ginie bez śladu i bez szansy na odtworzenie.</p>
 */
public record UpdateLibraryEntryRequest(
    String djNotes,
    List<String> customTags,
    Integer rating,
    String djSlotOverride,
    @NotNull Integer version) {

}
