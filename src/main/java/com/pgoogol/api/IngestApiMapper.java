package com.pgoogol.api;

import com.pgoogol.ingestion.IngestReport;
import com.pgoogol.ingestion.MetricsIngestReport;
import com.pgoogol.ingestion.PlaylistIngestReport;
import com.pgoogol.ingestion.RowError;
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

    public IngestMetricsResponse toResponse(MetricsIngestReport report) {

        return new IngestMetricsResponse(report.applied(), report.matchedByIsrc(),
            toRowErrors(report.skipped()), toRowErrors(report.failed()));
    }

    private List<IngestMetricsResponse.RowErrorResponse> toRowErrors(List<RowError> errors) {

        return errors.stream()
            .map(error -> new IngestMetricsResponse.RowErrorResponse(error.line(), error.reason()))
            .toList();
    }

    public IngestPlaylistResponse toResponse(PlaylistIngestReport report) {

        List<IngestPlaylistResponse.SkippedItemResponse> skipped = report.skipped().stream()
            .map(item -> new IngestPlaylistResponse.SkippedItemResponse(
                item.position(), item.reason()))
            .toList();
        return new IngestPlaylistResponse(report.playlistId(), report.spotifyPlaylistId(),
            report.name(), report.tracks(), report.imported(), report.alreadyExisted(), skipped);
    }
}
