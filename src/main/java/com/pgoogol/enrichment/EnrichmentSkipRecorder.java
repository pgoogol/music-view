package com.pgoogol.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Zapisuje utwory pominięte przez job wzbogacania (D37). Job przechodzi przez
 * całą listę i pomija to, co padło, zamiast wywracać się na pierwszym błędzie —
 * przy 2500 utworach jedna felerna odpowiedź zewnętrznego API kasowała wcześniej
 * efekt całego przebiegu.
 *
 * <p>Komunikat obcinamy: do raportu idzie zdanie, nie ślad stosu. Pełny stos
 * i tak jest w logu, a kolumna {@code reason} ma być czytelna w UI.</p>
 */
@Component
@StepScope
public class EnrichmentSkipRecorder implements SkipListener<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSkipRecorder.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final EnrichmentFailureRepository failureRepository;
    private final long jobExecutionId;

    public EnrichmentSkipRecorder(EnrichmentFailureRepository failureRepository,
                                  @Value("#{stepExecution}") StepExecution stepExecution) {

        this.failureRepository = failureRepository;
        this.jobExecutionId = stepExecution.getJobExecutionId();
    }

    @Override
    public void onSkipInWrite(String spotifyId, Throwable failure) {
        record(spotifyId, failure);
    }

    @Override
    public void onSkipInProcess(String spotifyId, Throwable failure) {
        record(spotifyId, failure);
    }

    @Override
    public void onSkipInRead(Throwable failure) {
        // czytanie to zapytanie po id-kach; jego awaria nie dotyczy konkretnego utworu
        log.warn("Pominięto wiersz przy czytaniu zakresu wzbogacania", failure);
    }

    private void record(String spotifyId, Throwable failure) {

        String reason = describe(failure);
        log.warn("Utwór {} pominięty przez job {}: {}", spotifyId, jobExecutionId, reason, failure);
        failureRepository.record(jobExecutionId, spotifyId, reason);
    }

    /**
     * Wyjątki typowane ({@code AppException}) mają komunikat pisany dla
     * użytkownika, więc idą w całości; reszcie dokładamy nazwę klasy, bo samo
     * „null" albo „0" nie mówi nic (docs/rules/errorhandling.md).
     */
    private String describe(Throwable failure) {

        String message = failure.getMessage();
        String described = Objects.isNull(message) || message.isBlank()
            ? failure.getClass().getSimpleName()
            : "%s: %s".formatted(failure.getClass().getSimpleName(), message);
        return described.length() <= MAX_REASON_LENGTH
            ? described
            : described.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }
}
