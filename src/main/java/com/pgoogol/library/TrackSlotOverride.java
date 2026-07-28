package com.pgoogol.library;

/**
 * Ręczne nadpisanie slotu wieczoru (D9) dla utworu — projekcja pod planowanie
 * setu, żeby nie ciągnąć całych wpisów biblioteki.
 */
public record TrackSlotOverride(String spotifyId, String djSlotOverride) {

}
