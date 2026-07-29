package com.pgoogol.ingestion;

import java.util.List;

/**
 * Raport importu metryk (D24): {@code applied} to liczba utworów katalogu,
 * którym metryki zostały zapisane (jeden wiersz CSV może trafić w kilka wydań
 * tego samego nagrania dopasowanych po ISRC), {@code matchedByIsrc} — ile wierszy
 * weszło dopiero po ISRC, {@code skipped} — wiersze bez utworu w katalogu,
 * {@code failed} — wiersze odrzucone przez parser.
 */
public record MetricsIngestReport(int applied, int matchedByIsrc,
                                  List<RowError> skipped, List<RowError> failed) {

}
