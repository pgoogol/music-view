package com.pgoogol.enrichment;

/**
 * Liczba utworów z brakami per grupa pól (D11) — zasila missing-count w API.
 */
public record MissingFieldsCount(long metadata, long audio, long ai) {

}
