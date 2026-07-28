package com.pgoogol.api;

import java.util.List;

/**
 * Raport z importu pliku CSV — kontrakt {@code POST /api/ingest/file}.
 */
public record IngestFileResponse(int imported, int alreadyExisted, List<FailedRowResponse> failed) {

    public record FailedRowResponse(long line, String reason) {

    }
}
