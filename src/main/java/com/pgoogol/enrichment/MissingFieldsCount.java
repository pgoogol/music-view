package com.pgoogol.enrichment;

/**
 * Liczba utworów z brakami per grupa pól (D11) — zasila missing-count w API.
 * {@code lyrics} liczy utwory bez rozstrzygniętego tekstu (D32).
 */
public record MissingFieldsCount(long metadata, long audio, long ai, long lyrics) {

}
