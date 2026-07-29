package com.pgoogol.api;

import java.util.List;

/**
 * Raport z importu CSV z metrykami — kontrakt {@code POST /api/ingest/metrics} (D24).
 */
public record IngestMetricsResponse(int applied, int matchedByIsrc,
                                    List<RowErrorResponse> skipped,
                                    List<RowErrorResponse> failed) {

    public record RowErrorResponse(long line, String reason) {

    }
}
