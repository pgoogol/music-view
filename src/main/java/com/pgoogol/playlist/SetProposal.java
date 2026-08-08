package com.pgoogol.playlist;

import com.pgoogol.catalog.TrackCatalog;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Propozycja setu (M4.2) — <b>nic nie zapisuje</b> (D26). Playlistę zakłada DJ
 * istniejącą drogą, a to jest materiał do obejrzenia i poprawienia.
 *
 * <p>{@code seed} wraca w odpowiedzi także wtedy, gdy nie podano go w żądaniu:
 * bez tego nie dałoby się wrócić do propozycji, którą przed chwilą widziało
 * się na ekranie.</p>
 *
 * @param notes czego nie dało się domknąć (np. za mało utworów na szczyt) —
 *              lepsza odpowiedź niż błąd, bo krótszy set też jest użyteczny
 */
public record SetProposal(
    List<ProposedTrack> tracks,
    long totalDurationMs,
    long targetDurationMs,
    long seed,
    List<String> notes) {

    public record ProposedTrack(int position, TrackCatalog track, @Nullable DjSlot djSlot) {

    }
}
