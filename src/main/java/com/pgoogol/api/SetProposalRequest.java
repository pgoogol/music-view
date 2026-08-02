package com.pgoogol.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Żądanie propozycji setu (M4.2) — czas wieczoru plus te same filtry, którymi
 * DJ zawęża wyszukiwarkę. {@code seed} pozwala wrócić do wcześniejszej
 * propozycji; bez niego każde wywołanie daje inny układ (D26).
 */
public record SetProposalRequest(
    @NotNull @Min(15) @Max(720) Integer targetMinutes,
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
    BigDecimal livenessMax) {

}
