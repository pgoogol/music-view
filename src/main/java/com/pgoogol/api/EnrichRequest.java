package com.pgoogol.api;

import com.pgoogol.enrichment.EnrichmentScope;
import com.pgoogol.enrichment.FieldGroup;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Zlecenie wzbogacania (D10): zakres + grupy pól; {@code spotifyIds}
 * wymagane dla SINGLE/SELECTED.
 */
public record EnrichRequest(
    @NotNull EnrichmentScope scope,
    @NotEmpty Set<FieldGroup> fields,
    List<String> spotifyIds) {

    public List<String> spotifyIdsOrEmpty() {
        return Objects.requireNonNullElse(spotifyIds, List.of());
    }
}
