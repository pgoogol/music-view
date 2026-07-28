package com.pgoogol.ingestion;

import java.util.List;

public record CsvParseResult(List<ParsedTrack> tracks, List<RowError> errors) {

}
