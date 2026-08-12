package com.pgoogol.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Żądanie dobrania utworu na wskazane miejsce w secie (M4.4, D32).
 *
 * @param position miejsce wstawienia: 0 przed pierwszym utworem, {@code null}
 *                 albo długość setu — na koniec
 * @param limit    ilu kandydatów pokazać (domyślnie 5, najwyżej 20)
 */
public record SetSuggestionRequest(
    @Min(0) Integer position,
    @Min(1) @Max(20) Integer limit,
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
