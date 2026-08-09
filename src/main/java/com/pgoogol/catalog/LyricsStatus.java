package com.pgoogol.catalog;

import java.util.Set;

/**
 * Stan tekstu utworu (D32). Trzy stany są <b>rozstrzygnięte</b> — zakres
 * MISSING ich nie rusza, więc LRCLIB nie jest pytany drugi raz o to samo
 * (negatywny cache w duchu D18). {@link #FETCHED} rozstrzygnięty nie jest:
 * tekst mamy, ale tłumaczenie nie powstało (np. provider LLM padł w trakcie),
 * więc utwór wraca do kolejki.
 */
public enum LyricsStatus {

    /** Tekst pobrany, przetłumaczony i zinterpretowany. */
    TRANSLATED,

    /** LRCLIB nie zna tekstu tego nagrania. */
    NOT_FOUND,

    /** LRCLIB zna nagranie i mówi wprost, że jest instrumentalne. */
    INSTRUMENTAL,

    /** Tekst pobrany, tłumaczenia jeszcze nie ma. */
    FETCHED;

    private static final Set<LyricsStatus> RESOLVED = Set.of(TRANSLATED, NOT_FOUND, INSTRUMENTAL);

    public boolean isResolved() {
        return RESOLVED.contains(this);
    }
}
