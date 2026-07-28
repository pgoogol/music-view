package com.pgoogol.catalog;

/**
 * Biała lista porządków sortowania wyszukiwarki katalogu (M3.1). Enum jest
 * kontraktem API — kolumny nie przychodzą z zewnątrz jako tekst, więc nie ma
 * jak wstrzyknąć fragmentu SQL-a (docs/rules/security.md).
 *
 * <p>{@link #RELEVANCE} to zachowanie domyślne: przy zapytaniu tekstowym
 * kolejność wg trafności, bez zapytania — alfabetycznie wykonawca/tytuł.
 */
public enum CatalogSort {

    RELEVANCE,
    TITLE,
    ARTIST,
    YEAR,
    BPM,
    POPULARITY,
    DURATION,
    ENERGY
}
