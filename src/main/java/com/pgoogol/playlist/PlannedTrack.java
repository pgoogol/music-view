package com.pgoogol.playlist;

import com.pgoogol.catalog.TrackCatalog;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

/**
 * Utwór na pozycji setu wraz z wyliczonym slotem (D9). {@code djSlot} to slot
 * obowiązujący — czyli override DJ-a, a gdy go nie ma, wynik
 * {@link DjSlotCalculator}; {@code null} oznacza utwór jeszcze niewzbogacony.
 *
 * <p>{@code loudnessDb} i {@code timeSignature} pochodzą z metryk wgranych
 * z pliku (D24) i jadą tu wyłącznie po to, żeby planer setu mógł ostrzec
 * o skoku głośności i o metrum innym niż 4/4 (D25). Nie są filtrami i nie
 * wchodzą do rekordu katalogu — set ich potrzebuje, katalog nie.</p>
 */
public record PlannedTrack(
    int position,
    TrackCatalog track,
    @Nullable DjSlot djSlot,
    @Nullable String djSlotOverride,
    @Nullable BigDecimal loudnessDb,
    @Nullable Integer timeSignature) {

}
