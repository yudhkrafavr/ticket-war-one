package com.ticket_war.war.repository;

import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.enums.WarBatchStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WarBatchRepository {

    private final EntityManager entityManager;

    public WarBatchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<WarBatch> findAllByWarSessionId(UUID warSessionId) {
        return entityManager.createQuery("""
                        select batch from WarBatch batch
                        where batch.warSessionId = :sessionId
                        order by batch.batchNumber asc
                        """, WarBatch.class)
                .setParameter("sessionId", warSessionId)
                .getResultList();
    }

    public List<WarBatch> findAllForUpdateByWarSessionId(UUID warSessionId) {
        return entityManager.createQuery("""
                        select batch from WarBatch batch
                        where batch.warSessionId = :sessionId
                        order by batch.batchNumber asc
                        """, WarBatch.class)
                .setParameter("sessionId", warSessionId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    public int openPendingBatches(UUID warSessionId) {
        return entityManager.createQuery("""
                        update WarBatch batch
                        set batch.status = :waiting
                        where batch.warSessionId = :sessionId and batch.status = :pending
                        """)
                .setParameter("waiting", WarBatchStatus.WAITING_ROOM_OPEN)
                .setParameter("sessionId", warSessionId)
                .setParameter("pending", WarBatchStatus.PENDING)
                .executeUpdate();
    }

    public int markInProgress(UUID id, UUID warSessionId, Instant updatedAt) {
        return entityManager.createQuery("""
                        update WarBatch batch
                        set batch.status = :inProgress, batch.updatedAt = :updatedAt
                        where batch.id = :id and batch.warSessionId = :sessionId
                          and batch.status = :waiting
                        """)
                .setParameter("inProgress", WarBatchStatus.IN_PROGRESS)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .setParameter("sessionId", warSessionId)
                .setParameter("waiting", WarBatchStatus.WAITING_ROOM_OPEN)
                .executeUpdate();
    }

    public Optional<WarBatch> findByIdAndWarSessionId(UUID id, UUID warSessionId) {
        return entityManager.createQuery("""
                        select batch from WarBatch batch
                        where batch.id = :id and batch.warSessionId = :sessionId
                        """, WarBatch.class)
                .setParameter("id", id)
                .setParameter("sessionId", warSessionId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public WarBatch insert(WarBatch batch) {
        entityManager.persist(batch);
        entityManager.flush();
        return batch;
    }

    public int update(WarBatch batch) {
        WarBatch current = entityManager.find(WarBatch.class, batch.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (current == null || !current.getWarSessionId().equals(batch.getWarSessionId())) {
            return 0;
        }
        current.setParticipantCapacity(batch.getParticipantCapacity());
        current.setTicketQuota(batch.getTicketQuota());
        current.setUpdatedAt(batch.getUpdatedAt());
        return 1;
    }

    public int delete(UUID id, UUID warSessionId) {
        return entityManager.createQuery("""
                        delete from WarBatch batch
                        where batch.id = :id and batch.warSessionId = :sessionId
                        """)
                .setParameter("id", id)
                .setParameter("sessionId", warSessionId)
                .executeUpdate();
    }
}
