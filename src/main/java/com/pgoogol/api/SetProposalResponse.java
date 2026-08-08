package com.pgoogol.api;

import java.util.List;

/**
 * Propozycja setu (M4.2) — kontrakt {@code POST /api/sets/propose}. Nic nie
 * zostało zapisane (D26): to materiał do obejrzenia, a set zakłada DJ przez
 * {@code POST /api/playlists} i {@code POST /api/playlists/{id}/tracks}.
 *
 * @param seed  ziarno użyte przy losowaniu — podaj je z powrotem, żeby dostać
 *              tę samą propozycję
 * @param notes czego nie dało się domknąć (za mało utworów na fazę, krótszy set)
 */
public record SetProposalResponse(
    int trackCount,
    long totalDurationMs,
    long targetDurationMs,
    long seed,
    List<String> notes,
    List<ProposedTrackResponse> tracks) {

    public record ProposedTrackResponse(int position, String djSlot, TrackResponse track) {

    }
}
