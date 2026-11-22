package com.ticketwar.event_service.repository;

import com.ticketwar.event_service.entity.BookingEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingRepository {

    private final EntityManager entityManager;

    public BookingRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Locks the owning War batch row so booking updates for that batch are serialized. */
    public boolean lockWarBatch(UUID warSessionId, UUID warBatchId) {
        return !entityManager.createNativeQuery("""
                        SELECT id FROM war_schema.war_batches
                        WHERE id = ? AND war_session_id = ?
                        FOR UPDATE
                        """)
                .setParameter(1, warBatchId)
                .setParameter(2, warSessionId)
                .getResultList()
                .isEmpty();
    }

    public List<BatchKey> findBatchesNeedingAdvance(Instant now) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT DISTINCT booking.war_session_id, booking.war_batch_id
                        FROM booking_schema.bookings booking
                        WHERE booking.status = 'INVITED'
                          AND booking.expires_at <= ?
                        ORDER BY booking.war_session_id, booking.war_batch_id
                        """)
                .setParameter(1, now)
                .getResultList();
        List<BatchKey> keys = new ArrayList<>(rows.size());
        for (Object result : rows) {
            Object[] row = (Object[]) result;
            keys.add(new BatchKey((UUID) row[0], (UUID) row[1]));
        }
        return keys;
    }

    public List<UUID> findExpiredBookingIds(UUID warSessionId, UUID warBatchId, Instant now) {
        List<BookingEntity> bookings = entityManager.createQuery("""
                        select booking from BookingEntity booking
                        where booking.warSessionId = :sessionId
                          and booking.warBatchId = :batchId
                          and booking.status = 'INVITED'
                          and booking.expiresAt <= :now
                        order by booking.ticketNumber
                        """, BookingEntity.class)
                .setParameter("sessionId", warSessionId)
                .setParameter("batchId", warBatchId)
                .setParameter("now", now)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return bookings.stream().map(BookingEntity::getId).toList();
    }

    public boolean hasActiveAlternate(UUID warSessionId, UUID warBatchId, Instant now) {
        return !entityManager.createNativeQuery("""
                        SELECT 1
                        FROM booking_schema.bookings booking
                        JOIN war_schema.war_participants participant
                          ON participant.id = booking.participant_id
                        WHERE booking.war_session_id = ?
                          AND booking.war_batch_id = ?
                          AND booking.status = 'INVITED'
                          AND booking.expires_at > ?
                          AND participant.status = 'ALTERNATE'
                        ORDER BY booking.invited_at
                        LIMIT 1
                        """)
                .setParameter(1, warSessionId)
                .setParameter(2, warBatchId)
                .setParameter(3, now)
                .getResultList()
                .isEmpty();
    }

    public Optional<Integer> findOpenExpiredTicket(UUID warSessionId, UUID warBatchId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT expired.ticket_number
                        FROM booking_schema.bookings expired
                        WHERE expired.war_session_id = ?
                          AND expired.war_batch_id = ?
                          AND expired.status = 'EXPIRED'
                          AND NOT EXISTS (
                              SELECT 1 FROM booking_schema.bookings active
                              WHERE active.war_session_id = expired.war_session_id
                                AND active.war_batch_id = expired.war_batch_id
                                AND active.ticket_number = expired.ticket_number
                                AND active.status IN ('INVITED', 'CLAIMED')
                          )
                        ORDER BY expired.ticket_number
                        LIMIT 1
                        """)
                .setParameter(1, warSessionId)
                .setParameter(2, warBatchId)
                .getResultList();
        return rows.stream().findFirst().map(value -> ((Number) value).intValue());
    }

    public Optional<BookingRecord> findLatestBooking(UUID participantId) {
        return entityManager.createQuery("""
                        select booking from BookingEntity booking
                        where booking.participantId = :participantId
                        order by booking.invitedAt desc
                        """, BookingEntity.class)
                .setParameter("participantId", participantId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(this::toRecord);
    }

    public boolean hasBooking(UUID warSessionId, UUID participantId) {
        Long count = entityManager.createQuery("""
                        select count(booking) from BookingEntity booking
                        where booking.warSessionId = :sessionId
                          and booking.participantId = :participantId
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .setParameter("participantId", participantId)
                .getSingleResult();
        return count > 0;
    }

    public Optional<BookingRecord> findBookingByEmailTokenHash(String tokenHash) {
        return entityManager.createQuery("""
                        select booking from BookingEntity booking
                        where booking.emailTokenHash = :tokenHash
                        """, BookingEntity.class)
                .setParameter("tokenHash", tokenHash)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .map(this::toRecord);
    }

    public Optional<BookingRecord> lockBooking(UUID bookingId) {
        BookingEntity booking = entityManager.find(
                BookingEntity.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(booking).map(this::toRecord);
    }

    public boolean hasUnclaimedTicket(UUID warSessionId, UUID warBatchId, int ticketQuota) {
        Long claimedCount = entityManager.createQuery("""
                        select count(distinct booking.ticketNumber) from BookingEntity booking
                        where booking.warSessionId = :sessionId
                          and booking.warBatchId = :batchId
                          and booking.status = 'CLAIMED'
                        """, Long.class)
                .setParameter("sessionId", warSessionId)
                .setParameter("batchId", warBatchId)
                .getSingleResult();
        return claimedCount < ticketQuota;
    }

    public void createBooking(
            UUID warSessionId,
            UUID warBatchId,
            UUID participantId,
            int ticketNumber,
            String emailTokenHash,
            Instant invitedAt,
            Instant expiresAt) {
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setWarSessionId(warSessionId);
        booking.setWarBatchId(warBatchId);
        booking.setParticipantId(participantId);
        booking.setTicketNumber(ticketNumber);
        booking.setEmailTokenHash(emailTokenHash);
        booking.setStatus("INVITED");
        booking.setInvitedAt(invitedAt);
        booking.setExpiresAt(expiresAt);
        entityManager.persist(booking);
    }

    public void expireBooking(UUID bookingId, Instant now) {
        BookingEntity booking = entityManager.find(
                BookingEntity.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (booking != null && "INVITED".equals(booking.getStatus())) {
            booking.setStatus("EXPIRED");
            booking.setResolvedAt(now);
        }
    }

    public boolean claimBooking(UUID bookingId, Instant now) {
        BookingEntity booking = entityManager.find(
                BookingEntity.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (booking == null || !"INVITED".equals(booking.getStatus())
                || !booking.getExpiresAt().isAfter(now)) {
            return false;
        }
        booking.setStatus("CLAIMED");
        booking.setResolvedAt(now);
        return true;
    }

    private BookingRecord toRecord(BookingEntity booking) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT batch_number FROM war_schema.war_batches
                        WHERE id = ? AND war_session_id = ?
                        """)
                .setParameter(1, booking.getWarBatchId())
                .setParameter(2, booking.getWarSessionId())
                .getResultList();
        Integer batchNumber = rows.isEmpty() ? null : ((Number) rows.getFirst()).intValue();
        return new BookingRecord(booking.getId(), booking.getWarSessionId(), booking.getWarBatchId(),
                booking.getParticipantId(), booking.getTicketNumber(), batchNumber,
                booking.getExpiresAt(), booking.getStatus());
    }

    public record BatchKey(UUID warSessionId, UUID warBatchId) {
    }

    public record BookingRecord(
            UUID id,
            UUID warSessionId,
            UUID warBatchId,
            UUID participantId,
            int ticketNumber,
            Integer batchNumber,
            Instant expiresAt,
            String status) {
    }
}
