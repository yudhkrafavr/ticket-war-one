package com.ticket_war.war.repository;

import com.ticket_war.war.entity.TriviaOption;
import com.ticket_war.war.entity.TriviaQuestion;
import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarSessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WarSessionRepository {

    private final EntityManager entityManager;

    public WarSessionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public WarSession insert(WarSession session) {
        entityManager.persist(session);
        return session;
    }

    public List<WarSession> findAll(WarSessionStatus status) {
        if (status == null) {
            return entityManager.createQuery("""
                            select session from WarSession session
                            order by session.createdAt desc
                            """, WarSession.class)
                    .getResultList();
        }
        return entityManager.createQuery("""
                        select session from WarSession session
                        where session.status = :status
                        order by session.createdAt desc
                        """, WarSession.class)
                .setParameter("status", status)
                .getResultList();
    }

    public Optional<WarSession> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(WarSession.class, id));
    }

    public Optional<WarSession> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(WarSession.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public int updateDraft(WarSession session) {
        WarSession current = entityManager.find(WarSession.class, session.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (current == null || current.getStatus() != WarSessionStatus.DRAFT) {
            return 0;
        }
        current.setEventId(session.getEventId());
        current.setName(session.getName());
        current.setDescription(session.getDescription());
        current.setTotalParticipants(session.getTotalParticipants());
        current.setTotalBatches(session.getTotalBatches());
        current.setTotalTickets(session.getTotalTickets());
        current.setQuestionsPerAttempt(session.getQuestionsPerAttempt());
        current.setChallengeDurationSeconds(session.getChallengeDurationSeconds());
        current.setRegistrationOpensAt(session.getRegistrationOpensAt());
        current.setRegistrationClosesAt(session.getRegistrationClosesAt());
        current.setWaitingRoomOpensAt(session.getWaitingRoomOpensAt());
        current.setChallengeStartsAt(session.getChallengeStartsAt());
        current.setUpdatedAt(session.getUpdatedAt());
        return 1;
    }

    public int publishDraft(UUID id, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarSession session
                        set session.status = :scheduled, session.updatedAt = :updatedAt
                        where session.id = :id and session.status = :draft
                        """)
                .setParameter("scheduled", WarSessionStatus.SCHEDULED)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .setParameter("draft", WarSessionStatus.DRAFT)
                .executeUpdate();
    }

    public int openWaitingRoom(UUID id, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarSession session
                        set session.status = :waiting, session.updatedAt = :updatedAt
                        where session.id = :id and session.status = :scheduled
                        """)
                .setParameter("waiting", WarSessionStatus.WAITING_ROOM_OPEN)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .setParameter("scheduled", WarSessionStatus.SCHEDULED)
                .executeUpdate();
    }

    public int markInProgress(UUID id, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarSession session
                        set session.status = :inProgress, session.updatedAt = :updatedAt
                        where session.id = :id
                          and session.status in (:scheduled, :waiting)
                        """)
                .setParameter("inProgress", WarSessionStatus.IN_PROGRESS)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .setParameter("scheduled", WarSessionStatus.SCHEDULED)
                .setParameter("waiting", WarSessionStatus.WAITING_ROOM_OPEN)
                .executeUpdate();
    }

    public int finalizeLeaderboard(UUID id, Instant finalizedAt) {
        return entityManager.createQuery("""
                        update WarSession session
                        set session.status = :completed,
                            session.leaderboardFinalized = true,
                            session.leaderboardFinalizedAt = :finalizedAt,
                            session.updatedAt = :finalizedAt
                        where session.id = :id and session.leaderboardFinalized = false
                        """)
                .setParameter("completed", WarSessionStatus.COMPLETED)
                .setParameter("finalizedAt", finalizedAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    public BatchSummary getBatchSummary(UUID warSessionId) {
        Object[] summary = entityManager.createQuery("""
                        select count(batch), coalesce(sum(batch.participantCapacity), 0),
                               coalesce(sum(batch.ticketQuota), 0)
                        from WarBatch batch where batch.warSessionId = :sessionId
                        """, Object[].class)
                .setParameter("sessionId", warSessionId)
                .getSingleResult();
        return new BatchSummary(
                ((Number) summary[0]).longValue(),
                ((Number) summary[1]).longValue(),
                ((Number) summary[2]).longValue());
    }

    public long countTriviaQuestions(UUID warSessionId) {
        return entityManager.createQuery("""
                        select count(question) from TriviaQuestion question
                        where question.warSessionId = :sessionId
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .getSingleResult();
    }

    public boolean hasQuestionWithoutExactlyOneCorrectOption(UUID warSessionId) {
        Long invalidQuestions = entityManager.createQuery("""
                        select count(question) from TriviaQuestion question
                        where question.warSessionId = :sessionId
                          and (select count(option.id) from TriviaOption option
                               where option.questionId = question.id and option.isCorrect = true) <> 1
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .getSingleResult();
        return invalidQuestions > 0;
    }

    public record BatchSummary(long batchCount, long participantCapacitySum, long ticketQuotaSum) {
    }
}
