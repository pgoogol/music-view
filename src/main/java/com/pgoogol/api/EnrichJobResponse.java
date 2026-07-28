package com.pgoogol.api;

import com.pgoogol.enrichment.EnrichmentJobStatus;

import java.time.LocalDateTime;

/**
 * Status/postęp wykonania joba wzbogacania.
 */
public record EnrichJobResponse(
    long executionId,
    long jobInstanceId,
    String status,
    String scope,
    String fields,
    long readCount,
    long writeCount,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String exitDescription) {

    public static EnrichJobResponse from(EnrichmentJobStatus status) {

        return new EnrichJobResponse(
            status.executionId(),
            status.jobInstanceId(),
            status.status(),
            status.scope(),
            status.fields(),
            status.readCount(),
            status.writeCount(),
            status.startTime(),
            status.endTime(),
            status.exitDescription());
    }
}
