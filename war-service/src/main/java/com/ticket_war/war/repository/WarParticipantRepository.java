package com.ticket_war.war.repository;

import com.ticket_war.war.dto.response.AdminParticipantResponse;
import com.ticket_war.war.entity.WarParticipant;
import com.ticket_war.war.enums.WarParticipantStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WarParticipantRepository {

    private final EntityManager entityManager;

    public WarParticipantRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long countByWarSessionId(UUID warSessionId) {
        return entityManager.createQuery("""
                        select count(participant) from WarParticipant participant
                        where participant.warSessionId = :sessionId
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .getSingleResult();
    }

    public Map<UUID, Long> countByWarSessionIds(List<UUID> warSessionIds) {
        if (warSessionIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> counts = entityManager.createQuery("""
                        select participant.warSessionId, count(participant)
                        from WarParticipant participant
                        where participant.warSessionId in :sessionIds
                        group by participant.warSessionId
                        """, Object[].class)
                .setParameter("sessionIds", warSessionIds)
                .getResultList();
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : counts) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    public List<AdminParticipantResponse> findAdminRosterByWarSessionId(UUID warSessionId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT participant.id,
                               participant.email,
                               participant.registered_at,
                               participant.queue_entered_at,
                               batch.batch_number,
                               participant.queue_position,
                               participant.status
                        FROM war_schema.war_participants participant
                        LEFT JOIN war_schema.war_batches batch
                          ON batch.id = participant.batch_id
                         AND batch.war_session_id = participant.war_session_id
                        WHERE participant.war_session_id = ?
                        ORDER BY participant.registered_at, participant.id
                        """)
                .setParameter(1, warSessionId)
                .getResultList();
        return rows.stream().map(result -> {
            Object[] row = (Object[]) result;
            return new AdminParticipantResponse(
                    (UUID) row[0],
                    (String) row[1],
                    asInstant(row[2]),
                    asInstant(row[3]),
                    asInteger(row[4]),
                    asLong(row[5]),
                    WarParticipantStatus.valueOf((String) row[6]));
        }).toList();
    }

    public boolean existsByWarSessionIdAndEmail(UUID warSessionId, String email) {
        Long matches = entityManager.createQuery("""
                        select count(participant) from WarParticipant participant
                        where participant.warSessionId = :sessionId and participant.email = :email
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .setParameter("email", email)
                .getSingleResult();
        return matches > 0;
    }

    public WarParticipant insert(WarParticipant participant) {
        entityManager.persist(participant);
        entityManager.flush();
        return participant;
    }

    public Optional<WarParticipant> findByAccessTokenHash(String accessTokenHash) {
        return entityManager.createQuery("""
                        select participant from WarParticipant participant
                        where participant.accessTokenHash = :accessTokenHash
                        """, WarParticipant.class)
                .setParameter("accessTokenHash", accessTokenHash)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<WarParticipant> findByIdForUpdate(UUID participantId) {
        return Optional.ofNullable(entityManager.find(
                WarParticipant.class, participantId, LockModeType.PESSIMISTIC_WRITE));
    }

    public int updateStatus(UUID participantId, WarParticipantStatus expectedStatus,
            WarParticipantStatus newStatus, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarParticipant participant
                        set participant.status = :newStatus, participant.updatedAt = :updatedAt
                        where participant.id = :participantId and participant.status = :expectedStatus
                        """)
                .setParameter("newStatus", newStatus)
                .setParameter("updatedAt", updatedAt)
                .setParameter("participantId", participantId)
                .setParameter("expectedStatus", expectedStatus)
                .executeUpdate();
    }

    public int assignToWaitingRoom(
            UUID participantId,
            UUID warSessionId,
            UUID batchId,
            Instant queueEnteredAt,
            long queuePosition,
            Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarParticipant participant
                        set participant.batchId = :batchId,
                            participant.queueEnteredAt = :queueEnteredAt,
                            participant.queuePosition = :queuePosition,
                            participant.status = :waiting,
                            participant.updatedAt = :updatedAt
                        where participant.id = :participantId
                          and participant.warSessionId = :sessionId
                          and participant.status = :preregistered
                          and participant.batchId is null
                          and participant.queueEnteredAt is null
                        """)
                .setParameter("batchId", batchId)
                .setParameter("queueEnteredAt", queueEnteredAt)
                .setParameter("queuePosition", queuePosition)
                .setParameter("waiting", WarParticipantStatus.WAITING)
                .setParameter("updatedAt", updatedAt)
                .setParameter("participantId", participantId)
                .setParameter("sessionId", warSessionId)
                .setParameter("preregistered", WarParticipantStatus.PREREGISTERED)
                .executeUpdate();
    }

    public BatchEntryStats getBatchEntryStats(UUID batchId) {
        Object[] stats = entityManager.createQuery("""
                        select count(participant), coalesce(max(participant.queuePosition), 0)
                        from WarParticipant participant
                        where participant.batchId = :batchId and participant.queueEnteredAt is not null
                        """, Object[].class)
                .setParameter("batchId", batchId)
                .getSingleResult();
        return new BatchEntryStats(
                ((Number) stats[0]).longValue(), ((Number) stats[1]).longValue());
    }

    public record BatchEntryStats(long enteredCount, long maxQueuePosition) {
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
}
