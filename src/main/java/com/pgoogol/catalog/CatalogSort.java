package com.pgoogol.catalog;

/**
 * Biała lista porządków sortowania wyszukiwarki katalogu (M3.1). Enum jest
 * kontraktem API — kolumny nie przychodzą z zewnątrz jako tekst, więc nie ma
 * jak wstrzyknąć fragmentu SQL-a (docs/rules/security.md).
 *
 * <p>{@link #RELEVANCE} to zachowanie domyślne: przy zapytaniu tekstowym
 * kolejność wg trafności, bez zapytania — alfabetycznie wykonawca/tytuł.
 *
 * <p>{@link #RATING} i {@link #ADDED_AT} porządkują wynik po danych prywatnych
 * DJ-a (D3) — utwory spoza biblioteki nie mają ich wcale, więc lądują na końcu
 * jak każdy inny brak (M5.6).
 */
public enum CatalogSort {

    RELEVANCE,
    TITLE,
    ARTIST,
    ALBUM,
    YEAR,
    BPM,
    POPULARITY,
    DURATION,
    DANCEABILITY,
    ENERGY,
    RATING,
    ADDED_AT
}
