package com.pgoogol.library;

import org.springframework.lang.Nullable;

/**
 * Dane prywatne DJ-a potrzebne generatorowi setu (M4.2): ocena i ewentualny
 * override slotu. Projekcja, nie encja — generator czyta pulę kandydatów
 * hurtem i nie ma po co ciągnąć całych wpisów biblioteki.
 */
public record TrackDjData(String spotifyId, @Nullable Integer rating,
                          @Nullable String djSlotOverride) {

}
