package com.pgoogol.enrichment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Historia wykonań joba wzbogacania jednym zapytaniem (M5.1/D28).
 *
 * <p>{@code JobExplorer} nie umie „ostatnie N wykonań dowolnej instancji", więc
 * {@link EnrichmentService} odpytywał wykonania osobno dla każdej instancji
 * i przycinał listę dopiero w pamięci. Tabele {@code BATCH_*} zakłada nasza
 * migracja V3, więc zapytanie wprost do nich mieści się w tym, czym i tak
 * zarządzamy — to nie jest sięganie do cudzych wnętrzności.</p>
 */
@Repository
public class EnrichmentJobHistory {

    private static final String RECENT = """
        select e.job_execution_id,
               e.job_instance_id,
               e.status,
               e.start_time,
               e.end_time,
               e.exit_message,
               coalesce(sum(s.read_count), 0)  as read_count,
               coalesce(sum(s.write_count), 0) as write_count,
               max(p_scope.parameter_value)    as scope,
               max(p_fields.parameter_value)   as fields
          from batch_job_execution e
          join batch_job_instance i
            on i.job_instance_id = e.job_instance_id and i.job_name = ?
          left join batch_step_execution s
            on s.job_execution_id = e.job_execution_id
          left join batch_job_execution_params p_scope
            on p_scope.job_execution_id = e.job_execution_id and p_scope.parameter_name = 'scope'
          left join batch_job_execution_params p_fields
            on p_fields.job_execution_id = e.job_execution_id and p_fields.parameter_name = 'fields'
         group by e.job_execution_id, e.job_instance_id, e.status, e.start_time, e.end_time,
                  e.exit_message
         order by e.job_execution_id desc
         limit ?
        """;

    private final JdbcTemplate jdbcTemplate;

    public EnrichmentJobHistory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EnrichmentJobStatus> recent(String jobName, int limit) {
        return jdbcTemplate.query(RECENT, this::toStatus, jobName, limit);
    }

    private EnrichmentJobStatus toStatus(ResultSet resultSet, int rowNumber) throws SQLException {

        return new EnrichmentJobStatus(
            resultSet.getLong("job_execution_id"),
            resultSet.getLong("job_instance_id"),
            resultSet.getString("status"),
            resultSet.getString("scope"),
            resultSet.getString("fields"),
            resultSet.getLong("read_count"),
            resultSet.getLong("write_count"),
            dateTime(resultSet.getTimestamp("start_time")),
            dateTime(resultSet.getTimestamp("end_time")),
            resultSet.getString("exit_message"));
    }

    private LocalDateTime dateTime(Timestamp timestamp) {

        return Optional.ofNullable(timestamp)
            .map(Timestamp::toLocalDateTime)
            .orElse(null);
    }
}
