package com.ticket_war.war.repository;

import com.ticket_war.war.enums.WarBatchStatus;
import com.ticket_war.war.enums.WarParticipantStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AdminLeaderboardRepository {

    private static final String SELECT_COLUMNS = """
            SELECT batch.id AS batch_id,
                   batch.batch_number,
                   batch.status AS batch_status,
                   batch.ticket_quota,
                   batch.participant_capacity,
                   participant.id AS participant_id,
                   participant.email,
                   participant.competition_rank,
                   participant.queue_position,
                   participant.queue_entered_at,
                   participant.status AS competition_result,
                   attempt.completion_duration_ms
            FROM war_schema.war_batches batch
            LEFT JOIN war_schema.war_participants participant
              ON participant.batch_id = batch.id
             AND participant.war_session_id = batch.war_session_id
            LEFT JOIN war_schema.challenge_attempts attempt
              ON attempt.participant_id = participant.id
            WHERE batch.war_session_id = ?
            """;

    private static final String ORDER_BY = """
            ORDER BY batch.batch_number ASC,
                     participant.competition_rank ASC NULLS LAST,
                     participant.queue_entered_at ASC NULLS LAST,
                     participant.queue_position ASC NULLS LAST,
                     participant.id ASC
            """;

    private final EntityManager entityManager;

    public AdminLeaderboardRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<LeaderboardRow> findByWarSessionId(UUID warSessionId) {
        return mapRows(entityManager.createNativeQuery(SELECT_COLUMNS + ORDER_BY)
                .setParameter(1, warSessionId)
                .getResultList());
    }

    public List<LeaderboardRow> findByWarSessionIdAndBatchId(UUID warSessionId, UUID batchId) {
        return mapRows(entityManager.createNativeQuery(SELECT_COLUMNS + " AND batch.id = ? " + ORDER_BY)
                .setParameter(1, warSessionId)
                .setParameter(2, batchId)
                .getResultList());
    }

    private static List<LeaderboardRow> mapRows(List<?> rows) {
        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            Object competitionResult = values[10];
            return new LeaderboardRow(
                    (UUID) values[0],
                    asInteger(values[1]),
                    WarBatchStatus.valueOf((String) values[2]),
                    asInteger(values[3]),
                    asInteger(values[4]),
                    (UUID) values[5],
                    (String) values[6],
                    asInteger(values[7]),
                    asLong(values[8]),
                    asInstant(values[9]),
                    asLong(values[11]),
                    competitionResult == null
                            ? null
                            : WarParticipantStatus.valueOf((String) competitionResult));
        }).toList();
    }

    private static Integer asInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Instant asInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalArgumentException("Unsupported database timestamp type: " + value.getClass());
    }

    public record LeaderboardRow(
            UUID batchId,
            Integer batchNumber,
            WarBatchStatus batchStatus,
            Integer ticketQuota,
            Integer participantCapacity,
            UUID participantId,
            String email,
            Integer competitionRank,
            Long queuePosition,
            Instant queueEnteredAt,
            Long completionDurationMs,
            WarParticipantStatus competitionResult
    ) {
    }
}
