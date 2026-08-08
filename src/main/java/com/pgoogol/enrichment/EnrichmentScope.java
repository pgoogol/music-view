package com.pgoogol.enrichment;

/**
 * Zakres zlecenia wzbogacania (D10): pojedynczy utwór, zaznaczone, wszystkie
 * z brakami w wybranych grupach pól albo — od M5.1 — utwory opisane starszym
 * modelem lub promptem niż bieżąca konfiguracja (D28).
 *
 * <p>{@code OUTDATED} domyka pętlę zaprojektowaną w D3/D15: pola {@code model_used}
 * i {@code enrich_version} zapisujemy właśnie po to, żeby dało się przeliczyć
 * <b>same estymaty</b> bez ponownego odpytywania źródeł faktów. Dlatego zakres
 * dotyczy wyłącznie grupy AI.</p>
 */
public enum EnrichmentScope {

    SINGLE,
    SELECTED,
    MISSING,
    OUTDATED
}
