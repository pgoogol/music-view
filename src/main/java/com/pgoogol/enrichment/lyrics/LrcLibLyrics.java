package com.pgoogol.enrichment.lyrics;

import org.springframework.lang.Nullable;

/**
 * Tekst nagrania zwrócony przez LRCLIB (D32). {@code instrumental} to
 * odpowiedź serwisu „to nagranie nie ma tekstu", a nie brak danych — dlatego
 * kończy wzbogacanie tak samo skutecznie jak znaleziony tekst.
 *
 * @param plainLyrics tekst bez znaczników czasu; {@code null} dla nagrania
 *                    instrumentalnego
 */
public record LrcLibLyrics(@Nullable Long lrclibId, boolean instrumental, @Nullable String plainLyrics) {

    public static LrcLibLyrics instrumental(@Nullable Long lrclibId) {
        return new LrcLibLyrics(lrclibId, true, null);
    }
}
