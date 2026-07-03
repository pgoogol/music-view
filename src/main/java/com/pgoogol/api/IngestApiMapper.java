package com.pgoogol.api;

import com.pgoogol.ingestion.IngestReport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestApiMapper {

    public IngestFileResponse toResponse(IngestReport report) {

        List<IngestFileResponse.FailedRowResponse> failed = report.failed().stream()
            .map(error -> new IngestFileResponse.FailedRowResponse(error.line(), error.reason()))
            .toList();
        return new IngestFileResponse(report.imported(), report.alreadyExisted(), failed);
    }
}
