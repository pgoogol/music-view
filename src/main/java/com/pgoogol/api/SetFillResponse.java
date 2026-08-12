package com.pgoogol.api;

import java.util.List;

/**
 * Dalszy ciąg gotowego setu (M4.4) — kontrakt {@code POST /api/sets/{id}/fill}.
 * Jak generator (D26), <b>nic nie zostało zapisane</b>: utwory dopisuje DJ przez
 * {@code POST /api/playlists/{id}/tracks}, a {@code position} w każdym wpisie
 * mówi, na którym miejscu wylądują.
 *
 * @param currentTrackCount ile utworów set ma przed uzupełnieniem
 * @param currentDurationMs ile set trwa przed uzupełnieniem
 * @param totalDurationMs   ile będzie trwał po dopisaniu propozycji
 * @param seed              ziarno losowania — podaj je z powrotem, żeby dostać
 *                          ten sam dalszy ciąg
 * @param notes             czego nie dało się domknąć (pula się skończyła, set
 *                          już jest dłuższy od zamówionego czasu)
 */
public record SetFillResponse(
    int currentTrackCount,
    long currentDurationMs,
    int addedTrackCount,
    long totalDurationMs,
    long targetDurationMs,
    long seed,
    List<String> notes,
    List<SetProposalResponse.ProposedTrackResponse> tracks) {

}
