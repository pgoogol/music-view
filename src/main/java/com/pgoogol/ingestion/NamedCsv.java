package com.pgoogol.ingestion;

import org.springframework.core.io.InputStreamSource;

/**
 * Wgrany plik CSV: nazwa (trafia do raportu i do {@code manual_metrics.source})
 * plus źródło treści otwierane dopiero na czas parsowania — przy imporcie kilku
 * plików naraz nie trzymamy ich wszystkich w pamięci.
 */
public record NamedCsv(String name, InputStreamSource content) {

}
