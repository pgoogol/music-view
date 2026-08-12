package com.pgoogol.api;

import java.math.BigDecimal;

/**
 * Filtry puli utworów wspólne dla wszystkich żądań z {@code /api/sets} — te same,
 * którymi DJ zawęża wyszukiwarkę (M4.1). Trzy kontrakty ({@code propose},
 * {@code fill}, {@code suggest}) opisują je płasko, bo tak wygląda JSON, ale
 * mapowanie na {@code CatalogSearchCriteria} jest jedno.
 */
public interface SetFilters {

    String search();

    String genreFamily();

    Integer bpmMin();

    Integer bpmMax();

    String tempoClass();

    String energy();

    Boolean inLibrary();

    Integer ratingMin();

    String tag();

    String camelot();

    Boolean camelotCompatible();

    BigDecimal valenceMin();

    BigDecimal valenceMax();

    BigDecimal instrumentalMin();

    BigDecimal livenessMax();
}
