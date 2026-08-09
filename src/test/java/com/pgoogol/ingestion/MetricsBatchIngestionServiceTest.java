package com.pgoogol.ingestion;

import com.pgoogol.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Wgrywanie kilku plików z metrykami naraz (D24) — plik odrzucony w całości
 * nie zabiera ze sobą pozostałych.
 */
@ExtendWith(MockitoExtension.class)
class MetricsBatchIngestionServiceTest {

    @Mock
    private MetricsIngestionService metricsIngestionService;

    @InjectMocks
    private MetricsBatchIngestionService service;

    @Test
    void ingestAll_whenOneFileIsRejected_importsTheRestAndNamesTheBrokenOne() {

        // given
        given(metricsIngestionService.ingest(any(), eq("wesela.csv")))
            .willReturn(new MetricsIngestReport(3, 1, List.of(), List.of()));
        given(metricsIngestionService.ingest(any(), eq("zepsuty.csv")))
            .willThrow(new ValidationException("CSV_MISSING_COLUMNS",
                "Plik CSV nie zawiera kolumny identyfikującej utwór"));
        given(metricsIngestionService.ingest(any(), eq("salsa.csv")))
            .willReturn(new MetricsIngestReport(2, 0, List.of(), List.of()));

        // when
        List<MetricsFileReport> reports = service.ingestAll(List.of(
            csv("wesela.csv"), csv("zepsuty.csv"), csv("salsa.csv")));

        // then
        assertThat(reports).extracting(MetricsFileReport::file)
            .containsExactly("wesela.csv", "zepsuty.csv", "salsa.csv");
        assertThat(reports.get(0)).isInstanceOfSatisfying(MetricsFileReport.Imported.class,
            imported -> assertThat(imported.report().applied()).isEqualTo(3));
        assertThat(reports.get(1)).isInstanceOfSatisfying(MetricsFileReport.Failed.class, failed -> {
            assertThat(failed.errorCode()).isEqualTo("CSV_MISSING_COLUMNS");
            assertThat(failed.reason()).contains("identyfikującej utwór");
        });
        assertThat(reports.get(2)).isInstanceOfSatisfying(MetricsFileReport.Imported.class,
            imported -> assertThat(imported.report().applied()).isEqualTo(2));
    }

    @Test
    void ingestAll_whenFileCannotBeRead_reportsItWithoutBreakingTheBatch() {

        // given
        given(metricsIngestionService.ingest(any(), eq("dobry.csv")))
            .willReturn(new MetricsIngestReport(1, 0, List.of(), List.of()));

        // when
        List<MetricsFileReport> reports = service.ingestAll(List.of(unreadable("urwany.csv"), csv("dobry.csv")));

        // then
        assertThat(reports.get(0)).isInstanceOfSatisfying(MetricsFileReport.Failed.class,
            failed -> assertThat(failed.errorCode()).isEqualTo("FILE_UNREADABLE"));
        assertThat(reports.get(1)).isInstanceOf(MetricsFileReport.Imported.class);
    }

    private NamedCsv csv(String name) {

        return new NamedCsv(name, new ByteArrayResource(
            "Spotify Track Id,BPM\n2c7nzxJYmPtkimDdrhcfJx,96\n".getBytes(StandardCharsets.UTF_8)));
    }

    private NamedCsv unreadable(String name) {

        return new NamedCsv(name, () -> {
            throw new IOException("upload przerwany");
        });
    }
}
