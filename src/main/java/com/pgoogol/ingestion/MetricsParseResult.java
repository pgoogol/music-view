package com.pgoogol.ingestion;

import java.util.List;

public record MetricsParseResult(List<ParsedMetrics> rows, List<RowError> errors) {

}
