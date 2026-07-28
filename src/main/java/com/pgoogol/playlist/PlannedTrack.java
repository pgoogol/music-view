package com.pgoogol.playlist;

import com.pgoogol.catalog.TrackCatalog;

/**
 * Utwór na pozycji setu wraz z wyliczonym slotem (D9). {@code djSlot} to slot
 * obowiązujący — czyli override DJ-a, a gdy go nie ma, wynik
 * {@link DjSlotCalculator}; {@code null} oznacza utwór jeszcze niewzbogacony.
 */
public record PlannedTrack(
    int position,
    TrackCatalog track,
    DjSlot djSlot,
    String djSlotOverride) {

}
