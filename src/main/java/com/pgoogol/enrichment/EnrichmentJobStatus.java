package com.pgoogol.enrichment;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import java.time.LocalDateTime;

/**
 * Status/postęp wykonania joba wzbogacania — dla API M1.7 i testów DoD.
 */
public record EnrichmentJobStatus(
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

    public static EnrichmentJobStatus from(JobExecution execution) {

        long readCount = execution.getStepExecutions().stream()
            .mapToLong(StepExecution::getReadCount).sum();
        long writeCount = execution.getStepExecutions().stream()
            .mapToLong(StepExecution::getWriteCount).sum();
        return new EnrichmentJobStatus(
            execution.getId(),
            execution.getJobInstance().getInstanceId(),
            execution.getStatus().name(),
            execution.getJobParameters().getString("scope"),
            execution.getJobParameters().getString("fields"),
            readCount,
            writeCount,
            execution.getStartTime(),
            execution.getEndTime(),
            execution.getExitStatus().getExitDescription());
    }
}
