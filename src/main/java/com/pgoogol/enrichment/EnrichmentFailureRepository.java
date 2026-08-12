package com.pgoogol.enrichment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Powody, dla których pojedyncze utwory wypadły z przebiegu wzbogacania (D37).
 *
 * <p>Zapis idzie <b>własną transakcją</b> ({@code REQUIRES_NEW}): wołamy go
 * z {@code SkipListener}, czyli w środku chunka, który Spring Batch właśnie
 * wycofuje. Bez tego powód porażki znikałby razem z rollbackiem tego chunka
 * i zostawałby sam licznik pominięć — czyli dokładnie ta informacja, której
 * brak zmusza do czytania logów.</p>
 */
@Repository
public class EnrichmentFailureRepository {

    private static final String INSERT = """
        insert into enrichment_failure (job_execution_id, spotify_id, reason, failed_at)
        values (?, ?, ?, ?)
        """;

    private static final String BY_EXECUTION = """
        select spotify_id, reason, failed_at
          from enrichment_failure
         where job_execution_id = ?
         order by id
         limit ?
        """;

    private static final String COUNT_BY_EXECUTION =
        "select count(*) from enrichment_failure where job_execution_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate ownTransaction;

    public EnrichmentFailureRepository(JdbcTemplate jdbcTemplate,
                                       PlatformTransactionManager transactionManager) {

        this.jdbcTemplate = jdbcTemplate;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(long jobExecutionId, String spotifyId, String reason) {

        ownTransaction.executeWithoutResult(status ->
            jdbcTemplate.update(INSERT, jobExecutionId, spotifyId, reason,
                Timestamp.from(Instant.now())));
    }

    public List<EnrichmentFailure> byExecution(long jobExecutionId, int limit) {
        return jdbcTemplate.query(BY_EXECUTION, this::toFailure, jobExecutionId, limit);
    }

    public long countByExecution(long jobExecutionId) {

        Long count = jdbcTemplate.queryForObject(COUNT_BY_EXECUTION, Long.class, jobExecutionId);
        return count == null ? 0 : count;
    }

    private EnrichmentFailure toFailure(ResultSet resultSet, int rowNumber) throws SQLException {

        return new EnrichmentFailure(
            resultSet.getString("spotify_id"),
            resultSet.getString("reason"),
            resultSet.getTimestamp("failed_at").toInstant());
    }
}
