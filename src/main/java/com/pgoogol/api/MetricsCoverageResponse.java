package com.pgoogol.api;

/**
 * Pokrycie katalogu metrykami wgranymi z pliku (D24) — kontrakt
 * {@code GET /api/catalog/metrics-coverage}. Filtry po metrykach (M4.1)
 * działają wyłącznie na {@code withMetrics} utworach, więc UI pokazuje
 * tę parę przy filtrach: pusty wynik ma być czytany jako brak danych,
 * nie jako awaria.
 */
public record MetricsCoverageResponse(long withMetrics, long total) {

}
