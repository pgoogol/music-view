package com.pgoogol.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Ręczne dodanie utworu do biblioteki; tytuł/wykonawca budują szkielet
 * katalogu, gdy utworu jeszcze w nim nie ma.
 */
public record AddLibraryTrackRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String spotifyId,
    @NotBlank String title,
    @NotBlank String artist,
    String album) {

}
