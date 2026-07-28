package com.pgoogol.ingestion;

import java.util.List;

/**
 * Raport importu (M1.2): nowe wpisy, duplikaty (w bibliotece lub w samym pliku)
 * i odrzucone wiersze.
 */
public record IngestReport(int imported, int alreadyExisted, List<RowError> failed) {

}
