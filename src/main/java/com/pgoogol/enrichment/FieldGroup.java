package com.pgoogol.enrichment;

/**
 * Grupy pól wzbogacania (D11): METADATA — fakty ze Spotify, AUDIO — fakty
 * z AcousticBrainz/Deezer, AI — estymacje LLM, LYRICS — tekst z LRCLIB wraz
 * z tłumaczeniem i interpretacją (D32).
 *
 * <p>LYRICS i AI to dwa różne zastosowania tego samego providera LLM: AI opisuje
 * utwór DJ-owi z metadanych, LYRICS pracuje na pobranym tekście. Rozdzielone,
 * bo mają inny koszt (tekst to kilka tysięcy znaków wejścia), inne źródło
 * i inną wersję promptu.</p>
 */
public enum FieldGroup {

    METADATA,
    AUDIO,
    AI,
    LYRICS
}
