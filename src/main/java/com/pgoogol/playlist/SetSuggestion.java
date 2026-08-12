package com.pgoogol.playlist;

import com.pgoogol.catalog.TrackCatalog;
import org.springframework.lang.Nullable;

/**
 * Kandydat na jedno miejsce w gotowym secie (M4.4, D32). Kolejność listy niesie
 * ocenę, więc sam wynik punktowy nie wychodzi na zewnątrz — DJ dostaje powody,
 * po których widać, dlaczego utwór pasuje.
 *
 * @param bpmDelta różnica tempa wobec sąsiada, o który się opieramy (utwór przed
 *                 luką, a przy dobieraniu na początek — utwór po niej);
 *                 {@code null}, gdy któremuś z nich brakuje BPM
 * @param harmonic czy tonacje sąsiada i kandydata są zgodne na kole Camelot (D25);
 *                 {@code null} przy nieznanej tonacji — to brak danych, nie zderzenie
 */
public record SetSuggestion(
    TrackCatalog track,
    @Nullable DjSlot djSlot,
    @Nullable Integer bpmDelta,
    @Nullable Boolean harmonic) {

}
