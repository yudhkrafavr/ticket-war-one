package com.ticket_war.war.repository;

import com.ticket_war.war.entity.ChallengeAttempt;
import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.entity.WarParticipant;
import com.ticket_war.war.enums.ChallengeAttemptStatus;
import com.ticket_war.war.enums.WarBatchStatus;
import com.ticket_war.war.enums.WarParticipantStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class LeaderboardRepository {

    private final EntityManager entityManager;

    public LeaderboardRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<UUID> findDueSessionIds() {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery("""
                        SELECT id
                        FROM war_schema.war_sessions
                        WHERE leaderboard_finalized = FALSE
                          AND status IN ('SCHEDULED', 'WAITING_ROOM_OPEN', 'IN_PROGRESS', 'COMPLETED')
                          AND challenge_starts_at IS NOT NULL
                          AND challenge_starts_at
                              + challenge_duration_seconds * INTERVAL '1 second' <= CURRENT_TIMESTAMP
                        ORDER BY challenge_starts_at ASC
                        LIMIT 100
                        """)
                .getResultList();
        return ids;
    }

    public int expireUnresolvedAttempts(UUID warSessionId, Instant updatedAt) {
        return entityManager.createQuery("""
                        update ChallengeAttempt attempt
                        set attempt.status = :expired, attempt.updatedAt = :updatedAt
                        where attempt.status in (:ready, :inProgress)
                          and exists (
                              select participant.id from WarParticipant participant
                              where participant.id = attempt.participantId
                                and participant.warSessionId = :sessionId
                          )
                        """)
                .setParameter("expired", ChallengeAttemptStatus.EXPIRED)
                .setParameter("updatedAt", updatedAt)
                .setParameter("ready", ChallengeAttemptStatus.READY)
                .setParameter("inProgress", ChallengeAttemptStatus.IN_PROGRESS)
                .setParameter("sessionId", warSessionId)
                .executeUpdate();
    }

    public int markParticipantsLost(UUID warSessionId, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarParticipant participant
                        set participant.status = :lost,
                            participant.competitionRank = null,
                            participant.updatedAt = :updatedAt
                        where participant.warSessionId = :sessionId
                        """)
                .setParameter("lost", WarParticipantStatus.LOST)
                .setParameter("updatedAt", updatedAt)
                .setParameter("sessionId", warSessionId)
                .executeUpdate();
    }

    public int rankQualifiedParticipants(UUID warSessionId, Instant updatedAt) {
        List<?> rankedCandidates = entityManager.createNativeQuery("""
                        SELECT participant.id AS participant_id,
                               participant.batch_id,
                               batch.ticket_quota
                        FROM war_schema.war_participants participant
                        JOIN war_schema.challenge_attempts attempt
                          ON attempt.participant_id = participant.id
                        JOIN war_schema.war_batches batch
                          ON batch.id = participant.batch_id
                         AND batch.war_session_id = participant.war_session_id
                        WHERE participant.war_session_id = ?
                          AND attempt.status = 'SUBMITTED'
                          AND attempt.all_answers_correct = TRUE
                          AND attempt.completion_duration_ms IS NOT NULL
                        ORDER BY participant.batch_id,
                                 attempt.completion_duration_ms ASC,
                                 participant.queue_entered_at ASC NULLS LAST,
                                 participant.queue_position ASC NULLS LAST,
                                 participant.id ASC
                        """)
                .setParameter(1, warSessionId)
                .getResultList();

        if (rankedCandidates.isEmpty()) {
            return 0;
        }

        List<RankedCandidate> candidates = rankedCandidates.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new RankedCandidate((UUID) row[0], (UUID) row[1], ((Number) row[2]).intValue());
        }).toList();
        List<UUID> participantIds = candidates.stream().map(RankedCandidate::participantId).toList();
        Map<UUID, WarParticipant> participants = new HashMap<>();
        for (int start = 0; start < participantIds.size(); start += 1000) {
            List<UUID> idChunk = participantIds.subList(start, Math.min(start + 1000, participantIds.size()));
            entityManager.createQuery("""
                            select participant from WarParticipant participant
                            where participant.id in :participantIds
                            """, WarParticipant.class)
                    .setParameter("participantIds", idChunk)
                    .getResultList()
                    .forEach(participant -> participants.put(participant.getId(), participant));
        }

        Map<UUID, Integer> nextRankByBatch = new HashMap<>();
        for (RankedCandidate candidate : candidates) {
            int rank = nextRankByBatch.merge(candidate.batchId(), 1, Integer::sum);
            WarParticipant participant = participants.get(candidate.participantId());
            if (participant != null) {
                participant.setCompetitionRank(rank);
                participant.setStatus(rank <= candidate.ticketQuota()
                        ? WarParticipantStatus.WINNER
                        : WarParticipantStatus.ALTERNATE);
                participant.setUpdatedAt(updatedAt);
            }
        }
        return candidates.size();
    }

    public int completeBatches(UUID warSessionId, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarBatch batch
                        set batch.status = :completed, batch.updatedAt = :updatedAt
                        where batch.warSessionId = :sessionId and batch.status <> :completed
                        """)
                .setParameter("completed", WarBatchStatus.COMPLETED)
                .setParameter("updatedAt", updatedAt)
                .setParameter("sessionId", warSessionId)
                .executeUpdate();
    }

    public List<FinalizedResultRow> findFinalizedResults(UUID warSessionId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT batch.id AS batch_id,
                               batch.batch_number,
                               batch.ticket_quota,
                               participant.id AS participant_id,
                               participant.competition_rank,
                               participant.email,
                               participant.status
                        FROM war_schema.war_batches batch
                        JOIN war_schema.war_participants participant
                          ON participant.batch_id = batch.id
                         AND participant.war_session_id = batch.war_session_id
                        WHERE batch.war_session_id = ?
                          AND participant.status IN ('WINNER', 'ALTERNATE')
                        ORDER BY batch.batch_number ASC, participant.competition_rank ASC
                        """)
                .setParameter(1, warSessionId)
                .getResultList();

        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            return new FinalizedResultRow(
                    (UUID) values[0],
                    ((Number) values[1]).intValue(),
                    ((Number) values[2]).intValue(),
                    (UUID) values[3],
                    ((Number) values[4]).intValue(),
                    (String) values[5],
                    (String) values[6]);
        }).toList();
    }

    public record FinalizedResultRow(
            UUID batchId,
            int batchNumber,
            int ticketQuota,
            UUID participantId,
            int competitionRank,
            String email,
            String status) {
    }

    public void flushChanges() {
        entityManager.flush();
    }

    private record RankedCandidate(UUID participantId, UUID batchId, int ticketQuota) {
    }
}
