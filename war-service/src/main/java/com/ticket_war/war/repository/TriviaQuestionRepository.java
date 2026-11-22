package com.ticket_war.war.repository;

import com.ticket_war.war.entity.TriviaOption;
import com.ticket_war.war.entity.TriviaQuestion;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TriviaQuestionRepository {

    private final EntityManager entityManager;

    public TriviaQuestionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<TriviaQuestion> findAllByWarSessionId(UUID warSessionId) {
        return entityManager.createQuery("""
                        select question from TriviaQuestion question
                        where question.warSessionId = :sessionId
                        order by question.questionOrder asc
                        """, TriviaQuestion.class)
                .setParameter("sessionId", warSessionId)
                .getResultList();
    }

    public Optional<TriviaQuestion> findByIdAndWarSessionId(UUID id, UUID warSessionId) {
        return entityManager.createQuery("""
                        select question from TriviaQuestion question
                        where question.id = :id and question.warSessionId = :sessionId
                        """, TriviaQuestion.class)
                .setParameter("id", id)
                .setParameter("sessionId", warSessionId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public List<TriviaOption> findOptionsByQuestionId(UUID questionId) {
        return entityManager.createQuery("""
                        select option from TriviaOption option
                        where option.questionId = :questionId
                        order by option.optionOrder asc
                        """, TriviaOption.class)
                .setParameter("questionId", questionId)
                .getResultList();
    }

    public List<TriviaOption> findOptionsByQuestionIds(List<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select option from TriviaOption option
                        where option.questionId in :questionIds
                        order by option.questionId, option.optionOrder asc
                        """, TriviaOption.class)
                .setParameter("questionIds", questionIds)
                .getResultList();
    }

    public boolean existsQuestionOrder(UUID warSessionId, int questionOrder) {
        return countQuestionsAtOrder(warSessionId, questionOrder, null) > 0;
    }

    public boolean existsQuestionOrderExcluding(
            UUID warSessionId,
            int questionOrder,
            UUID excludedQuestionId) {
        return countQuestionsAtOrder(warSessionId, questionOrder, excludedQuestionId) > 0;
    }

    public boolean existsOptionOrder(UUID questionId, int optionOrder) {
        Long matches = entityManager.createQuery("""
                        select count(option) from TriviaOption option
                        where option.questionId = :questionId and option.optionOrder = :optionOrder
                        """, Long.class)
                .setParameter("questionId", questionId)
                .setParameter("optionOrder", optionOrder)
                .getSingleResult();
        return matches > 0;
    }

    public long countByWarSessionId(UUID warSessionId) {
        return entityManager.createQuery("""
                        select count(question) from TriviaQuestion question
                        where question.warSessionId = :sessionId
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .getSingleResult();
    }

    public TriviaQuestion insertQuestion(TriviaQuestion question) {
        entityManager.persist(question);
        entityManager.flush();
        return question;
    }

    public int updateQuestion(TriviaQuestion question) {
        TriviaQuestion current = entityManager.find(TriviaQuestion.class, question.getId());
        if (current == null || !current.getWarSessionId().equals(question.getWarSessionId())) {
            return 0;
        }
        current.setQuestionText(question.getQuestionText());
        current.setQuestionOrder(question.getQuestionOrder());
        current.setUpdatedAt(question.getUpdatedAt());
        return 1;
    }

    public int deleteQuestion(UUID id, UUID warSessionId) {
        return entityManager.createQuery("""
                        delete from TriviaQuestion question
                        where question.id = :id and question.warSessionId = :sessionId
                        """)
                .setParameter("id", id)
                .setParameter("sessionId", warSessionId)
                .executeUpdate();
    }

    public int insertOption(TriviaOption option) {
        entityManager.persist(option);
        entityManager.flush();
        return 1;
    }

    public int deleteOptionsByQuestionId(UUID questionId) {
        return entityManager.createQuery("""
                        delete from TriviaOption option where option.questionId = :questionId
                        """)
                .setParameter("questionId", questionId)
                .executeUpdate();
    }

    private long countQuestionsAtOrder(UUID warSessionId, int questionOrder, UUID excludedId) {
        String query = excludedId == null
                ? "select count(question) from TriviaQuestion question "
                    + "where question.warSessionId = :sessionId and question.questionOrder = :questionOrder"
                : "select count(question) from TriviaQuestion question "
                    + "where question.warSessionId = :sessionId and question.questionOrder = :questionOrder "
                    + "and question.id <> :excludedId";
        var typedQuery = entityManager.createQuery(query, Long.class)
                .setParameter("sessionId", warSessionId)
                .setParameter("questionOrder", questionOrder);
        if (excludedId != null) {
            typedQuery.setParameter("excludedId", excludedId);
        }
        return typedQuery.getSingleResult();
    }
}
