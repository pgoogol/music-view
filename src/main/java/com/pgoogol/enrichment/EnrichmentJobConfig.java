package com.pgoogol.enrichment;

import com.pgoogol.catalog.TrackCatalog;
import com.pgoogol.catalog.TrackCatalogRepository;
import com.pgoogol.common.ValidationException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Restartowalny job wzbogacania (M1.6, D10): reader utworów wg {@code scope}
 * (keyset po {@code spotify_id} — utwory „naprawione" w trakcie nie psują
 * stronicowania) → writer per chunk=5: {@link TrackEnricher} w kolejności
 * METADATA → AUDIO → AI + zapis inkrementalny do {@code track_catalog}.
 * Checkpointy w tabelach BATCH_* (migracja V3); restart dokańcza od
 * ostatniego zatwierdzonego chunka.
 */
@Configuration
public class EnrichmentJobConfig {

    public static final String JOB_NAME = "enrichmentJob";
    public static final int CHUNK_SIZE = 5;

    static final Pattern SAFE_SPOTIFY_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** Nazwa modelu trafia do klauzuli WHERE, więc przechodzi tę samą kontrolę co id. */
    static final Pattern SAFE_MODEL = Pattern.compile("[A-Za-z0-9._:/-]{1,128}");

    private static final int READER_PAGE_SIZE = 50;
    private static final String METADATA_MISSING_SQL =
        "(isrc is null or year is null or duration_ms is null)";
    private static final String AUDIO_MISSING_SQL =
        "(bpm is null or musical_key is null or danceability is null or tempo_class is null)";
    private static final String AI_MISSING_SQL =
        "(style is null or genre_family is null or lyrics_theme is null "
            + "or description_pl is null or energy is null)";

    @Bean
    public Job enrichmentJob(JobRepository jobRepository, Step enrichmentStep) {

        return new JobBuilder(JOB_NAME, jobRepository)
            .start(enrichmentStep)
            .build();
    }

    @Bean
    public Step enrichmentStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JdbcPagingItemReader<String> enrichmentTrackIdReader,
                               ItemWriter<String> enrichmentTrackWriter) {

        return new StepBuilder("enrichmentStep", jobRepository)
            .<String, String>chunk(CHUNK_SIZE, transactionManager)
            .reader(enrichmentTrackIdReader)
            .writer(enrichmentTrackWriter)
            .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<String> enrichmentTrackIdReader(
            DataSource dataSource,
            @Value("#{jobParameters['scope']}") String scope,
            @Value("#{jobParameters['fields']}") String fields,
            @Value("#{jobParameters['spotifyIds']}") String spotifyIds,
            @Value("#{jobParameters['outdatedModel']}") String outdatedModel,
            @Value("#{jobParameters['outdatedVersion']}") Long outdatedVersion) {

        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("select spotify_id");
        queryProvider.setFromClause("from track_catalog");
        queryProvider.setWhereClause(whereClause(
            EnrichmentScope.valueOf(scope), parseFields(fields), parseIds(spotifyIds),
            outdatedModel, outdatedVersion));
        queryProvider.setSortKeys(Map.of("spotify_id", Order.ASCENDING));
        return new JdbcPagingItemReaderBuilder<String>()
            .name("enrichmentTrackIdReader")
            .dataSource(dataSource)
            .queryProvider(queryProvider)
            .pageSize(READER_PAGE_SIZE)
            .rowMapper((resultSet, rowNumber) -> resultSet.getString(1))
            .build();
    }

    @Bean
    @StepScope
    public ItemWriter<String> enrichmentTrackWriter(TrackCatalogRepository trackCatalogRepository,
                                                    TrackEnricher trackEnricher,
                                                    @Value("#{jobParameters['fields']}") String fields) {

        Set<FieldGroup> fieldGroups = parseFields(fields);
        return chunk -> {
            List<TrackCatalog> tracks = trackCatalogRepository.findAllById(List.copyOf(chunk.getItems()));
            trackEnricher.enrich(tracks, fieldGroups);
            trackCatalogRepository.saveAll(tracks);
        };
    }

    /** Launcher asynchroniczny dla API — start joba nie blokuje żądania. */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("enrichment-"));
        launcher.afterPropertiesSet();
        return launcher;
    }

    static Set<FieldGroup> parseFields(String fields) {

        if (Objects.isNull(fields) || fields.isBlank()) {
            throw new ValidationException("ENRICH_FIELDS_EMPTY", "Brak grup pól do wzbogacenia");
        }
        return Arrays.stream(fields.split(","))
            .map(String::strip)
            .map(FieldGroup::valueOf)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(FieldGroup.class)));
    }

    private static List<String> parseIds(String spotifyIds) {

        if (Objects.isNull(spotifyIds) || spotifyIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(spotifyIds.split(",")).map(String::strip).toList();
    }

    private static String whereClause(EnrichmentScope scope, Set<FieldGroup> fields,
                                      List<String> ids, String outdatedModel,
                                      Long outdatedVersion) {

        return switch (scope) {
            case MISSING -> "where " + missingCondition(fields);
            case OUTDATED -> "where " + outdatedCondition(outdatedModel, outdatedVersion);
            case SINGLE, SELECTED -> "where spotify_id in (" + quotedIds(ids) + ")";
        };
    }

    /**
     * Utwór „nieaktualny" to taki, który <b>już był</b> opisany (stąd
     * {@code enriched_at is not null}), ale innym modelem albo inną wersją
     * promptu niż bieżąca konfiguracja. Utwór nigdy nieopisany należy do
     * zakresu MISSING, nie tutaj.
     */
    static String outdatedCondition(String model, Long version) {

        if (Objects.isNull(model) || !SAFE_MODEL.matcher(model).matches()) {
            throw new ValidationException("ENRICH_BAD_MODEL",
                "Nieprawidłowa nazwa modelu w parametrach joba: '%s'".formatted(model));
        }
        String versionCondition = Objects.isNull(version)
            ? "enrich_version is not null"
            : "enrich_version is distinct from " + version;
        return "(enriched_at is not null and (model_used is distinct from '%s' or %s))"
            .formatted(model, versionCondition);
    }

    private static String missingCondition(Set<FieldGroup> fields) {

        return fields.stream()
            .map(group -> switch (group) {
                case METADATA -> METADATA_MISSING_SQL;
                case AUDIO -> AUDIO_MISSING_SQL;
                case AI -> AI_MISSING_SQL;
            })
            .collect(Collectors.joining(" or "));
    }

    private static String quotedIds(List<String> ids) {

        if (ids.isEmpty()) {
            throw new ValidationException("ENRICH_IDS_REQUIRED",
                "Zakres SINGLE/SELECTED wymaga listy spotify_id");
        }
        // walidacja formatu wyklucza SQL injection — id trafiają do klauzuli IN
        ids.stream()
            .filter(id -> !SAFE_SPOTIFY_ID.matcher(id).matches())
            .findFirst()
            .ifPresent(bad -> {
                throw new ValidationException("ENRICH_BAD_ID",
                    "Nieprawidłowy spotify_id: '%s'".formatted(bad));
            });
        return ids.stream()
            .map(id -> "'" + id + "'")
            .collect(Collectors.joining(","));
    }
}
