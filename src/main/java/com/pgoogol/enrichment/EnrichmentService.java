package com.pgoogol.enrichment;

import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.NotFoundException;
import com.pgoogol.common.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Zlecenia wzbogacania (M1.6): walidacja zakresu, start joba (asynchronicznie),
 * status/postęp, restart nieudanego wykonania (dokańcza od checkpointu —
 * te same parametry → ta sama instancja joba) i missing-count per grupa pól.
 */
@Service
public class EnrichmentService {

    static final int MAX_SELECTED_TRACKS = 100;

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);
    private static final Set<String> RESTARTABLE_STATUSES = Set.of("FAILED", "STOPPED");

    private final JobLauncher asyncJobLauncher;
    private final Job enrichmentJob;
    private final JobExplorer jobExplorer;
    private final TrackCatalogRepository trackCatalogRepository;

    public EnrichmentService(@Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
                             Job enrichmentJob, JobExplorer jobExplorer,
                             TrackCatalogRepository trackCatalogRepository) {

        this.asyncJobLauncher = asyncJobLauncher;
        this.enrichmentJob = enrichmentJob;
        this.jobExplorer = jobExplorer;
        this.trackCatalogRepository = trackCatalogRepository;
    }

    public long start(EnrichmentScope scope, Set<FieldGroup> fields, List<String> spotifyIds) {

        Objects.requireNonNull(scope, "scope");
        validate(scope, fields, spotifyIds);
        JobParameters parameters = new JobParametersBuilder()
            .addString("scope", scope.name())
            .addString("fields", canonicalFields(fields))
            .addString("spotifyIds", String.join(",", spotifyIds))
            .addString("requestedAt", Instant.now().toString())
            .toJobParameters();
        JobExecution execution = launch(parameters);
        log.info("Wystartowano job wzbogacania: executionId={}, scope={}, fields={}, utwory={}",
            execution.getId(), scope, fields, spotifyIds.isEmpty() ? "wg zakresu" : spotifyIds.size());
        return execution.getId();
    }

    /** Restart nieudanego wykonania — Spring Batch dokańcza od ostatniego chunka. */
    public long restart(long executionId) {

        JobExecution failed = requireExecution(executionId);
        if (!RESTARTABLE_STATUSES.contains(failed.getStatus().name())) {
            throw new ValidationException("JOB_NOT_RESTARTABLE",
                "Wykonanie %d ma status %s — restart możliwy tylko dla FAILED/STOPPED"
                    .formatted(executionId, failed.getStatus()));
        }
        JobExecution restarted = launch(failed.getJobParameters());
        log.info("Restart joba wzbogacania: {} → nowe wykonanie {}", executionId, restarted.getId());
        return restarted.getId();
    }

    public EnrichmentJobStatus status(long executionId) {
        return EnrichmentJobStatus.from(requireExecution(executionId));
    }

    public List<EnrichmentJobStatus> listJobs(int limit) {

        return jobExplorer.getJobInstances(EnrichmentJobConfig.JOB_NAME, 0, limit).stream()
            .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
            .sorted(Comparator.comparing(JobExecution::getId).reversed())
            .limit(limit)
            .map(EnrichmentJobStatus::from)
            .toList();
    }

    public MissingFieldsCount missingCount() {

        TrackCatalogRepository.MissingCounts counts =
            trackCatalogRepository.countMissingByGroup();
        return new MissingFieldsCount(counts.getMetadata(), counts.getAudio(), counts.getAi());
    }

    private JobExecution launch(JobParameters parameters) {

        try {
            return asyncJobLauncher.run(enrichmentJob, parameters);
        } catch (org.springframework.batch.core.repository.JobRestartException
                 | org.springframework.batch.core.repository.JobExecutionAlreadyRunningException
                 | org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException
                 | org.springframework.batch.core.JobParametersInvalidException ex) {
            throw new IllegalStateException("Nie udało się uruchomić joba wzbogacania", ex);
        }
    }

    private JobExecution requireExecution(long executionId) {

        return Optional.ofNullable(jobExplorer.getJobExecution(executionId))
            .filter(execution -> Objects.equals(
                execution.getJobInstance().getJobName(), EnrichmentJobConfig.JOB_NAME))
            .orElseThrow(() -> new NotFoundException("JOB_NOT_FOUND",
                "Brak wykonania joba wzbogacania o id %d".formatted(executionId)));
    }

    private void validate(EnrichmentScope scope, Set<FieldGroup> fields, List<String> spotifyIds) {

        if (Objects.isNull(fields) || fields.isEmpty()) {
            throw new ValidationException("ENRICH_FIELDS_EMPTY", "Wybierz co najmniej jedną grupę pól");
        }
        Objects.requireNonNull(spotifyIds, "spotifyIds");
        switch (scope) {
            case SINGLE -> {
                if (spotifyIds.size() != 1) {
                    throw new ValidationException("ENRICH_IDS_REQUIRED",
                        "Zakres SINGLE wymaga dokładnie jednego spotify_id");
                }
            }
            case SELECTED -> {
                if (spotifyIds.isEmpty()) {
                    throw new ValidationException("ENRICH_IDS_REQUIRED",
                        "Zakres SELECTED wymaga listy spotify_id");
                }
                if (spotifyIds.size() > MAX_SELECTED_TRACKS) {
                    throw new ValidationException("ENRICH_TOO_MANY_TRACKS",
                        "Zakres SELECTED obsługuje maksymalnie %d utworów — dla większych partii użyj MISSING"
                            .formatted(MAX_SELECTED_TRACKS));
                }
            }
            case MISSING -> {
                if (!spotifyIds.isEmpty()) {
                    throw new ValidationException("ENRICH_IDS_UNEXPECTED",
                        "Zakres MISSING nie przyjmuje listy spotify_id");
                }
            }
        }
        spotifyIds.stream()
            .filter(id -> !EnrichmentJobConfig.SAFE_SPOTIFY_ID.matcher(id).matches())
            .findFirst()
            .ifPresent(bad -> {
                throw new ValidationException("ENRICH_BAD_ID",
                    "Nieprawidłowy spotify_id: '%s'".formatted(bad));
            });
    }

    private String canonicalFields(Set<FieldGroup> fields) {

        return EnumSet.copyOf(fields).stream()
            .map(Enum::name)
            .reduce((first, second) -> first + "," + second)
            .orElseThrow();
    }
}
