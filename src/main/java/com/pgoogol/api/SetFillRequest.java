package com.pgoogol.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Żądanie uzupełnienia gotowego setu (M4.4, D32). {@code targetMinutes} to
 * długość <b>całego</b> wieczoru, nie tego, co dochodzi — set na 90 minut
 * uzupełniany do 240 dostanie 150 minut dalszego ciągu. {@code curve} wybiera
 * kształt wieczoru (M4.5, D33); brak = {@code STANDARD}.
 */
public record SetFillRequest(
    @NotNull @Min(15) @Max(720) Integer targetMinutes,
    String curve,
    Long seed,
    String search,
    String genreFamily,
    Integer bpmMin,
    Integer bpmMax,
    String tempoClass,
    String energy,
    Boolean inLibrary,
    @Min(1) @Max(5) Integer ratingMin,
    String tag,
    String camelot,
    Boolean camelotCompatible,
    BigDecimal valenceMin,
    BigDecimal valenceMax,
    BigDecimal instrumentalMin,
    BigDecimal livenessMax) implements SetFilters {

}
