package com.pgoogol.api;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Raport z importu CSV z metrykami — kontrakt {@code POST /api/ingest/metrics} (D24).
 * Liczby na wierzchu są sumą całej partii, szczegóły (i numery wierszy, które bez
 * nazwy pliku nic nie znaczą) siedzą w {@code files}.
 */
public record IngestMetricsResponse(int applied, int matchedByIsrc, int skippedRows, int failedRows,
                                    List<FileReportResponse> files) {

    /** {@code error} niepuste = pliku nie udało się wczytać w całości. */
    public record FileReportResponse(String file, int applied, int matchedByIsrc,
                                     List<RowErrorResponse> skipped,
                                     List<RowErrorResponse> failed,
                                     @Nullable String errorCode,
                                     @Nullable String error) {

    }

    public record RowErrorResponse(long line, String reason) {

    }
}
