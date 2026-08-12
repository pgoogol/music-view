package com.pgoogol.api;

import java.util.List;

/**
 * Kandydaci na jedno miejsce w secie (M4.4) — kontrakt
 * {@code POST /api/sets/{id}/suggest}. Nic nie zostało zapisane (D32); lista
 * jest uszeregowana od najlepiej pasującego.
 *
 * @param position miejsce, dla którego dobieraliśmy (rozwinięte „na koniec")
 */
public record SetSuggestionResponse(int position, List<SuggestedTrackResponse> suggestions) {

    /**
     * @param bpmDelta różnica tempa wobec sąsiada, o który się opieramy;
     *                 {@code null}, gdy któremuś z nich brakuje BPM
     * @param harmonic zgodność tonacji na kole Camelot; {@code null} przy
     *                 nieznanej tonacji — to brak danych, nie zderzenie (D25)
     */
    public record SuggestedTrackResponse(
        String djSlot,
        Integer bpmDelta,
        Boolean harmonic,
        TrackResponse track) {

    }
}
