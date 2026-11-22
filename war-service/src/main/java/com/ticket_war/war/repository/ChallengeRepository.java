package com.ticket_war.war.repository;

import com.ticket_war.war.entity.ChallengeAnswer;
import com.ticket_war.war.entity.ChallengeAttempt;
import com.ticket_war.war.entity.ChallengeAttemptQuestion;
import com.ticket_war.war.entity.TriviaOption;
import com.ticket_war.war.entity.TriviaQuestion;
import com.ticket_war.war.enums.ChallengeAttemptStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChallengeRepository {

    private final EntityManager entityManager;

    public ChallengeRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<ChallengeAttempt> findAttemptByParticipantId(UUID participantId) {
        return findAttempt("select attempt from ChallengeAttempt attempt "
                + "where attempt.participantId = :participantId", participantId, null, false);
    }

    public Optional<ChallengeAttempt> findAttemptByParticipantIdForUpdate(UUID participantId) {
        return findAttempt("select attempt from ChallengeAttempt attempt "
                + "where attempt.participantId = :participantId", participantId, null, true);
    }

    public Optional<ChallengeAttempt> findAttemptByIdAndParticipantId(UUID attemptId, UUID participantId) {
        return findAttempt("select attempt from ChallengeAttempt attempt "
                + "where attempt.id = :attemptId and attempt.participantId = :participantId",
                participantId, attemptId, false);
    }

    public Optional<ChallengeAttempt> findAttemptByIdAndParticipantIdForUpdate(
            UUID attemptId,
            UUID participantId) {
        return findAttempt("select attempt from ChallengeAttempt attempt "
                + "where attempt.id = :attemptId and attempt.participantId = :participantId",
                participantId, attemptId, true);
    }

    public int activateReadyAttempt(UUID attemptId, Instant updatedAt) {
        return entityManager.createQuery("""
                        update ChallengeAttempt attempt
                        set attempt.status = :inProgress, attempt.updatedAt = :updatedAt
                        where attempt.id = :attemptId and attempt.status = :ready
                        """)
                .setParameter("inProgress", ChallengeAttemptStatus.IN_PROGRESS)
                .setParameter("updatedAt", updatedAt)
                .setParameter("attemptId", attemptId)
                .setParameter("ready", ChallengeAttemptStatus.READY)
                .executeUpdate();
    }

    public ChallengeAttempt insertAttempt(ChallengeAttempt attempt) {
        entityManager.persist(attempt);
        entityManager.flush();
        return attempt;
    }

    public List<UUID> selectRandomQuestionIds(UUID warSessionId, int questionCount) {
        return entityManager.createQuery("""
                        select question.id from TriviaQuestion question
                        where question.warSessionId = :sessionId
                          and (select count(option.id) from TriviaOption option
                               where option.questionId = question.id) >= 2
                          and (select count(option.id) from TriviaOption option
                               where option.questionId = question.id and option.isCorrect = true) = 1
                        order by function('random')
                        """, UUID.class)
                .setParameter("sessionId", warSessionId)
                .setMaxResults(questionCount)
                .getResultList();
    }

    public QuestionOptionStats getQuestionOptionStats(UUID questionId) {
        Object[] stats = entityManager.createQuery("""
                        select count(option),
                               sum(case when option.isCorrect = true then 1 else 0 end)
                        from TriviaOption option where option.questionId = :questionId
                        """, Object[].class)
                .setParameter("questionId", questionId)
                .getSingleResult();
        return new QuestionOptionStats(
                ((Number) stats[0]).longValue(),
                stats[1] == null ? 0 : ((Number) stats[1]).longValue());
    }

    public int insertAttemptQuestion(ChallengeAttemptQuestion attemptQuestion) {
        entityManager.persist(attemptQuestion);
        entityManager.flush();
        return 1;
    }

    public long countAttemptQuestions(UUID attemptId) {
        return entityManager.createQuery("""
                        select count(question) from ChallengeAttemptQuestion question
                        where question.attemptId = :attemptId
                        """, Long.class)
                .setParameter("attemptId", attemptId)
                .getSingleResult();
    }

    public List<AssignedQuestion> findAssignedQuestions(UUID attemptId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT assignment.id AS attempt_question_id,
                               assignment.question_id,
                               assignment.display_order,
                               question.question_text
                        FROM war_schema.challenge_attempt_questions assignment
                        JOIN war_schema.trivia_questions question
                          ON question.id = assignment.question_id
                        WHERE assignment.attempt_id = ?
                        ORDER BY assignment.display_order ASC
                        """)
                .setParameter(1, attemptId)
                .getResultList();
        return rows.stream().map(result -> {
            Object[] row = (Object[]) result;
            return new AssignedQuestion(
                    (UUID) row[0], (UUID) row[1],
                    ((Number) row[2]).intValue(), (String) row[3]);
        }).toList();
    }

    public List<ChallengeOption> findOptionsByQuestionIds(List<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select option from TriviaOption option
                        where option.questionId in :questionIds
                        order by option.questionId asc, option.optionOrder asc
                        """, TriviaOption.class)
                .setParameter("questionIds", questionIds)
                .getResultList()
                .stream()
                .map(option -> new ChallengeOption(
                        option.getId(), option.getQuestionId(),
                        option.getOptionText(), option.getOptionOrder()))
                .toList();
    }

    public List<AttemptQuestionAssignment> findAttemptQuestionAssignments(UUID attemptId) {
        return entityManager.createQuery("""
                        select assignment from ChallengeAttemptQuestion assignment
                        where assignment.attemptId = :attemptId
                        order by assignment.displayOrder asc
                        """, ChallengeAttemptQuestion.class)
                .setParameter("attemptId", attemptId)
                .getResultList()
                .stream()
                .map(assignment -> new AttemptQuestionAssignment(
                        assignment.getId(), assignment.getQuestionId(), assignment.getDisplayOrder()))
                .toList();
    }

    public boolean optionBelongsToQuestion(UUID optionId, UUID questionId) {
        Long matches = entityManager.createQuery("""
                        select count(option) from TriviaOption option
                        where option.id = :optionId and option.questionId = :questionId
                        """, Long.class)
                .setParameter("optionId", optionId)
                .setParameter("questionId", questionId)
                .getSingleResult();
        return matches > 0;
    }

    public Optional<UUID> findCorrectOptionId(UUID questionId) {
        return entityManager.createQuery("""
                        select option.id from TriviaOption option
                        where option.questionId = :questionId and option.isCorrect = true
                        """, UUID.class)
                .setParameter("questionId", questionId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    public int insertAnswer(ChallengeAnswer answer) {
        entityManager.persist(answer);
        entityManager.flush();
        return 1;
    }

    public int updateSubmittedAttempt(ChallengeAttempt attempt) {
        if (entityManager.contains(attempt)) {
            entityManager.detach(attempt);
        }
        return entityManager.createQuery("""
                        update ChallengeAttempt current
                        set current.submittedAt = :submittedAt,
                            current.completionDurationMs = :completionDurationMs,
                            current.allAnswersCorrect = :allAnswersCorrect,
                            current.autoSubmitted = :autoSubmitted,
                            current.status = :submitted,
                            current.updatedAt = :updatedAt
                        where current.id = :attemptId and current.status = :inProgress
                        """)
                .setParameter("submittedAt", attempt.getSubmittedAt())
                .setParameter("completionDurationMs", attempt.getCompletionDurationMs())
                .setParameter("allAnswersCorrect", attempt.getAllAnswersCorrect())
                .setParameter("autoSubmitted", attempt.getAutoSubmitted())
                .setParameter("submitted", ChallengeAttemptStatus.SUBMITTED)
                .setParameter("updatedAt", attempt.getUpdatedAt())
                .setParameter("attemptId", attempt.getId())
                .setParameter("inProgress", ChallengeAttemptStatus.IN_PROGRESS)
                .executeUpdate();
    }

    private Optional<ChallengeAttempt> findAttempt(
            String query,
            UUID participantId,
            UUID attemptId,
            boolean lock) {
        var typedQuery = entityManager.createQuery(query, ChallengeAttempt.class)
                .setParameter("participantId", participantId);
        if (attemptId != null) {
            typedQuery.setParameter("attemptId", attemptId);
        }
        if (lock) {
            typedQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return typedQuery.getResultList().stream().findFirst();
    }

    public record QuestionOptionStats(long optionCount, long correctOptionCount) {
    }

    public record AssignedQuestion(
            UUID attemptQuestionId,
            UUID questionId,
            Integer displayOrder,
            String questionText) {
    }

    public record ChallengeOption(
            UUID id,
            UUID questionId,
            String optionText,
            Integer optionOrder) {
    }

    public record AttemptQuestionAssignment(
            UUID attemptQuestionId,
            UUID questionId,
            Integer displayOrder) {
    }
}
