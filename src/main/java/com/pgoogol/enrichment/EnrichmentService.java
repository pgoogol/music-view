package com.pgoogol.enrichment;

import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.NotFoundException;
import com.pgoogol.common.ValidationException;
import com.pgoogol.enrichment.llm.LlmProperties;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Zlecenia wzbogacania (M1.6): walidacja zakresu, start joba (asynchronicznie),
 * status/postęp, restart nieudanego wykonania (dokańcza od checkpointu —
 * te same parametry → ta sama instancja joba) i missing-count per grupa pól.
 *
 * <p>Od M5.1 (D28) dochodzi zakres {@link EnrichmentScope#OUTDATED} (przeliczenie
 * estymat po zmianie modelu albo promptu), szacunek kosztu przed startem oraz
 * twardy sufit {@code llm.max-tracks-per-job} — {@code SELECTED} miał limit od
 * M1.6, a {@code MISSING} nie miał żadnego.</p>
 */
@Service
public class EnrichmentService {

    static final int MAX_SELECTED_TRACKS = 100;

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);
    private static final Set<String> RESTARTABLE_STATUSES = Set.of("FAILED", "STOPPED");

    private final JobLauncher asyncJobLauncher;
    private final Job enrichmentJob;
    private final JobExplorer jobExplorer;
    private final EnrichmentJobHistory jobHistory;
    private final TrackCatalogRepository trackCatalogRepository;
    private final EnrichmentCostEstimator costEstimator;
    private final LlmProperties llmProperties;

    public EnrichmentService(@Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
                             Job enrichmentJob, JobExplorer jobExplorer,
                             EnrichmentJobHistory jobHistory,
                             TrackCatalogRepository trackCatalogRepository,
                             EnrichmentCostEstimator costEstimator,
                             LlmProperties llmProperties) {

        this.asyncJobLauncher = asyncJobLauncher;
        this.enrichmentJob = enrichmentJob;
        this.jobExplorer = jobExplorer;
        this.jobHistory = jobHistory;
        this.trackCatalogRepository = trackCatalogRepository;
        this.costEstimator = costEstimator;
        this.llmProperties = llmProperties;
    }

    public long start(EnrichmentScope scope, Set<FieldGroup> fields, List<String> spotifyIds) {

        Objects.requireNonNull(scope, "scope");
        EnrichmentEstimate estimate = estimate(scope, fields, spotifyIds);
        requireWithinLimit(scope, estimate);

        JobParametersBuilder parameters = new JobParametersBuilder()
            .addString("scope", scope.name())
            .addString("fields", canonicalFields(fields))
            .addString("spotifyIds", String.join(",", spotifyIds))
            .addString("requestedAt", Instant.now().toString());
        if (scope == EnrichmentScope.OUTDATED) {
            // model i wersja wchodzą w tożsamość joba, żeby restart dokończył
            // dokładnie ten zakres, a nie ten wynikający z konfiguracji po zmianie
            parameters.addString("outdatedModel", requiredModel());
            llmProperties.promptVersionNumber()
                .ifPresent(version -> parameters.addLong("outdatedVersion", version.longValue()));
        }
        JobExecution execution = launch(parameters.toJobParameters());
        log.info("Wystartowano job wzbogacania: executionId={}, scope={}, fields={}, utwory={}, "
                + "szacunek kosztu={}",
            execution.getId(), scope, fields, estimate.trackCount(),
            Optional.ofNullable(estimate.estimatedCost())
                .map(cost -> "$" + cost)
                .orElse("nieznany (brak stawek w konfiguracji)"));
        return execution.getId();
    }

    /**
     * Ile utworów obejmie zlecenie i ile to będzie kosztowało — bez uruchamiania
     * czegokolwiek. UI pyta o to <b>przed</b> startem joba (D28).
     */
    public EnrichmentEstimate estimate(EnrichmentScope scope, Set<FieldGroup> fields,
                                       List<String> spotifyIds) {

        Objects.requireNonNull(scope, "scope");
        validate(scope, fields, spotifyIds);
        long trackCount = switch (scope) {
            case SINGLE, SELECTED -> spotifyIds.size();
            case MISSING -> trackCatalogRepository.countMissingForFields(
                fields.contains(FieldGroup.METADATA),
                fields.contains(FieldGroup.AUDIO),
                fields.contains(FieldGroup.AI),
                fields.contains(FieldGroup.LYRICS));
            case OUTDATED -> trackCatalogRepository.countOutdated(
                requiredModel(), llmProperties.promptVersionNumber().orElse(null));
        };
        long aiTracks = fields.contains(FieldGroup.AI) ? trackCount : 0;
        long lyricsTracks = fields.contains(FieldGroup.LYRICS) ? trackCount : 0;
        int limit = llmProperties.maxTracksPerJob();
        return new EnrichmentEstimate(
            trackCount,
            aiTracks,
            lyricsTracks,
            costEstimator.estimate(aiTracks, lyricsTracks).orElse(null),
            limit,
            trackCount <= limit);
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

    /**
     * Historia wykonań jednym zapytaniem niezależnie od jej długości (M5.1) —
     * szczegóły w {@link EnrichmentJobHistory}.
     */
    public List<EnrichmentJobStatus> listJobs(int limit) {
        return jobHistory.recent(EnrichmentJobConfig.JOB_NAME, Math.max(1, limit));
    }

    public MissingFieldsCount missingCount() {

        TrackCatalogRepository.MissingCounts counts =
            trackCatalogRepository.countMissingByGroup();
        return new MissingFieldsCount(
            counts.getMetadata(), counts.getAudio(), counts.getAi(), counts.getLyrics());
    }

    private void requireWithinLimit(EnrichmentScope scope, EnrichmentEstimate estimate) {

        if (estimate.withinLimit()) {
            return;
        }
        String cost = Optional.ofNullable(estimate.estimatedCost())
            .map(value -> ", szacunek kosztu $" + value)
            .orElse("");
        throw new ValidationException("ENRICH_TOO_MANY_TRACKS",
            ("Zakres %s obejmuje %d utworów przy limicie %d%s — zawęź zlecenie albo podnieś "
                + "llm.max-tracks-per-job w konfiguracji")
                .formatted(scope, estimate.trackCount(), estimate.limit(), cost));
    }

    private String requiredModel() {

        String model = llmProperties.model();
        if (Objects.isNull(model) || model.isBlank()) {
            throw new ValidationException("LLM_MODEL_NOT_CONFIGURED",
                "Zakres OUTDATED porównuje utwory z bieżącym modelem — ustaw llm.model (LLM_MODEL)");
        }
        return model;
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
            case MISSING -> requireNoIds(scope, spotifyIds);
            case OUTDATED -> {
                requireNoIds(scope, spotifyIds);
                // fakty nie zależą od modelu ani promptu, więc ich przeliczanie
                // byłoby wywołaniem cudzego API bez powodu (D28)
                if (!EnumSet.copyOf(fields).equals(EnumSet.of(FieldGroup.AI))) {
                    throw new ValidationException("ENRICH_OUTDATED_AI_ONLY",
                        "Zakres OUTDATED przelicza wyłącznie estymaty — wybierz samą grupę AI");
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

    private void requireNoIds(EnrichmentScope scope, List<String> spotifyIds) {

        if (!spotifyIds.isEmpty()) {
            throw new ValidationException("ENRICH_IDS_UNEXPECTED",
                "Zakres %s nie przyjmuje listy spotify_id".formatted(scope));
        }
    }

    private String canonicalFields(Set<FieldGroup> fields) {

        return EnumSet.copyOf(fields).stream()
            .map(Enum::name)
            .reduce((first, second) -> first + "," + second)
            .orElseThrow();
    }
}
