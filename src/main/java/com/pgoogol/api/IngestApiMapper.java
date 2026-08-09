package com.pgoogol.api;

import com.pgoogol.ingestion.IngestReport;
import com.pgoogol.ingestion.MetricsFileReport;
import com.pgoogol.ingestion.MyPlaylistsIngestReport;
import com.pgoogol.ingestion.PlaylistIngestReport;
import com.pgoogol.ingestion.RowError;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.ToIntFunction;

@Component
public class IngestApiMapper {

    public IngestFileResponse toResponse(IngestReport report) {

        List<IngestFileResponse.FailedRowResponse> failed = report.failed().stream()
            .map(error -> new IngestFileResponse.FailedRowResponse(error.line(), error.reason()))
            .toList();
        return new IngestFileResponse(report.imported(), report.alreadyExisted(), failed);
    }

    public IngestMetricsResponse toResponse(List<MetricsFileReport> fileReports) {

        List<IngestMetricsResponse.FileReportResponse> files = fileReports.stream()
            .map(this::toFileResponse)
            .toList();
        return new IngestMetricsResponse(
            sum(files, IngestMetricsResponse.FileReportResponse::applied),
            sum(files, IngestMetricsResponse.FileReportResponse::matchedByIsrc),
            sum(files, file -> file.skipped().size()),
            sum(files, file -> file.failed().size()),
            files);
    }

    private IngestMetricsResponse.FileReportResponse toFileResponse(MetricsFileReport fileReport) {

        return switch (fileReport) {
            case MetricsFileReport.Imported imported -> new IngestMetricsResponse.FileReportResponse(
                imported.file(), imported.report().applied(), imported.report().matchedByIsrc(),
                toRowErrors(imported.report().skipped()), toRowErrors(imported.report().failed()),
                null, null);
            case MetricsFileReport.Failed failed -> new IngestMetricsResponse.FileReportResponse(
                failed.file(), 0, 0, List.of(), List.of(), failed.errorCode(), failed.reason());
        };
    }

    private int sum(List<IngestMetricsResponse.FileReportResponse> files,
                    ToIntFunction<IngestMetricsResponse.FileReportResponse> field) {

        return files.stream().mapToInt(field).sum();
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

    public IngestMyPlaylistsResponse toResponse(MyPlaylistsIngestReport report) {

        List<IngestPlaylistResponse> imported = report.imported().stream()
            .map(this::toResponse)
            .toList();
        List<IngestMyPlaylistsResponse.FailedPlaylistResponse> failed = report.failed().stream()
            .map(playlist -> new IngestMyPlaylistsResponse.FailedPlaylistResponse(
                playlist.spotifyPlaylistId(), playlist.name(),
                playlist.errorCode(), playlist.reason()))
            .toList();
        return new IngestMyPlaylistsResponse(imported, failed);
    }
}
