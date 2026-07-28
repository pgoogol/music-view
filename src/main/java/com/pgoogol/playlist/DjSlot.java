package com.pgoogol.playlist;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Miejsce utworu w przebiegu wieczoru (D9): rozgrzewka → środek → szczyt →
 * zamknięcie, plus przerwa („na oddech"). Slot nie jest zapisywany w katalogu —
 * wylicza go {@link DjSlotCalculator}, a DJ może go nadpisać
 * w {@code library_entry.dj_slot_override}.
 */
public enum DjSlot {

    /** Rozgrzewka — parkiet dopiero się zapełnia. */
    WARMUP,

    /** Środek wieczoru — parkiet pracuje, ale bez szczytu. */
    MIDDLE,

    /** Szczyt — najmocniejsze kawałki wieczoru. */
    PEAK,

    /** Zamknięcie — energia jeszcze wysoka, tempo już schodzi. */
    CLOSING,

    /** Przerwa — wolny kawałek na oddech. */
    BREAK;

    /** Rozpoznaje nazwę slotu bez względu na wielkość liter; puste = brak wartości. */
    public static Optional<DjSlot> parse(String value) {

        if (Objects.isNull(value) || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(slot -> slot.name().equals(normalized))
            .findFirst();
    }
}
