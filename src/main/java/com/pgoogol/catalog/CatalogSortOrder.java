package com.pgoogol.catalog;

import org.springframework.data.domain.Sort;

import java.util.Objects;
import java.util.Optional;

/**
 * Porządek sortowania wyszukiwarki katalogu — pole z białej listy
 * ({@link CatalogSort}) plus kierunek. Domyślnie trafność rosnąco, czyli
 * zachowanie sprzed M3.1.
 */
public record CatalogSortOrder(CatalogSort field, Sort.Direction direction) {

    public static final CatalogSortOrder DEFAULT =
        new CatalogSortOrder(CatalogSort.RELEVANCE, Sort.Direction.ASC);

    public CatalogSortOrder {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
    }

    /** Braki (null z parametrów żądania) sprowadza do wartości domyślnych. */
    public static CatalogSortOrder of(CatalogSort field, Sort.Direction direction) {

        return new CatalogSortOrder(
            Optional.ofNullable(field).orElse(DEFAULT.field()),
            Optional.ofNullable(direction).orElse(DEFAULT.direction()));
    }
}
