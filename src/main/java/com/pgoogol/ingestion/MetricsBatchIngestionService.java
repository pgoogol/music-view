package com.pgoogol.ingestion;

import com.pgoogol.common.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Import metryk z kilku plików naraz (D24) — eksport analizatora idzie zwykle
 * per playlista, więc uzupełnienie biblioteki to kilkanaście plików pod rząd.
 *
 * <p>Każdy plik ma własną transakcję (osobne wywołanie
 * {@link MetricsIngestionService#ingest}), więc plik z felernym nagłówkiem
 * nie zabiera ze sobą tych, które weszły. Kolejność ma znaczenie tylko wtedy,
 * gdy ten sam utwór występuje w kilku plikach — wygrywa ostatni, tak samo jak
 * przy ponownym wgraniu tego samego pliku.</p>
 */
@Service
public class MetricsBatchIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MetricsBatchIngestionService.class);

    private final MetricsIngestionService metricsIngestionService;

    public MetricsBatchIngestionService(MetricsIngestionService metricsIngestionService) {

        this.metricsIngestionService = metricsIngestionService;
    }

    public List<MetricsFileReport> ingestAll(List<NamedCsv> files) {

        Objects.requireNonNull(files, "files");
        List<MetricsFileReport> reports = files.stream().map(this::ingestFile).toList();
        log.info("Import metryk z {} plików zakończony: nieudane pliki={}", reports.size(),
            reports.stream().filter(MetricsFileReport.Failed.class::isInstance).count());
        return reports;
    }

    private MetricsFileReport ingestFile(NamedCsv file) {

        try (InputStream content = file.content().getInputStream()) {
            return new MetricsFileReport.Imported(file.name(),
                metricsIngestionService.ingest(content, file.name()));
        } catch (IOException ex) {
            log.warn("Plik '{}' pominięty — nie udało się go odczytać", file.name(), ex);
            return new MetricsFileReport.Failed(file.name(), "FILE_UNREADABLE",
                "Nie udało się odczytać przesłanego pliku");
        } catch (AppException ex) {
            log.warn("Plik '{}' pominięty: {} — {}", file.name(), ex.getErrorCode(), ex.getMessage());
            return new MetricsFileReport.Failed(file.name(), ex.getErrorCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Plik '{}' pominięty — nieoczekiwany błąd", file.name(), ex);
            return new MetricsFileReport.Failed(file.name(), "INTERNAL_ERROR",
                "nieoczekiwany błąd importu — szczegóły w logach aplikacji");
        }
    }
}
