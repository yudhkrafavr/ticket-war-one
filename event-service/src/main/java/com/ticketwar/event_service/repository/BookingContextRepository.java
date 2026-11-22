package com.ticketwar.event_service.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingContextRepository {

    private final EntityManager entityManager;

    public BookingContextRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<ParticipantContext> findParticipantByTokenHash(String tokenHash) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT participant.id AS participant_id,
                               participant.war_session_id,
                               participant.batch_id,
                               participant.status AS participant_status,
                               batch.batch_number,
                               batch.ticket_quota,
                               ws.leaderboard_finalized
                        FROM war_schema.war_participants participant
                        JOIN war_schema.war_sessions ws
                          ON ws.id = participant.war_session_id
                        LEFT JOIN war_schema.war_batches batch
                          ON batch.id = participant.batch_id
                         AND batch.war_session_id = participant.war_session_id
                        WHERE participant.access_token_hash = ?
                        """)
                .setParameter(1, tokenHash)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new ParticipantContext(
                (UUID) row[0],
                (UUID) row[1],
                (UUID) row[2],
                (String) row[3],
                asInteger(row[4]),
                asInteger(row[5]),
                Boolean.TRUE.equals(row[6])));
    }

    public List<FinalizedBatch> findFinalizedBatchesMissingWinnerBookings() {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT ws.id AS war_session_id, batch.id AS war_batch_id
                        FROM war_schema.war_sessions ws
                        JOIN war_schema.war_batches batch ON batch.war_session_id = ws.id
                        WHERE ws.status = 'COMPLETED'
                          AND ws.leaderboard_finalized = TRUE
                          AND EXISTS (
                              SELECT 1 FROM war_schema.war_participants winner
                              WHERE winner.war_session_id = ws.id
                                AND winner.batch_id = batch.id
                                AND winner.status = 'WINNER'
                                AND NOT EXISTS (
                                    SELECT 1 FROM booking_schema.bookings booking
                                    WHERE booking.war_session_id = ws.id
                                      AND booking.participant_id = winner.id
                                )
                          )
                        ORDER BY ws.id, batch.id
                        """)
                .getResultList();
        return rows.stream().map(result -> {
            Object[] row = (Object[]) result;
            return new FinalizedBatch((UUID) row[0], (UUID) row[1]);
        }).toList();
    }

    public List<WinnerCandidate> findUnbookedWinners(UUID warSessionId, UUID warBatchId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT participant.id, participant.competition_rank, participant.email,
                               ws.name, batch.batch_number
                        FROM war_schema.war_participants participant
                        JOIN war_schema.war_sessions ws ON ws.id = participant.war_session_id
                        JOIN war_schema.war_batches batch ON batch.id = participant.batch_id
                        WHERE participant.war_session_id = ?
                          AND participant.batch_id = ?
                          AND participant.status = 'WINNER'
                          AND NOT EXISTS (
                              SELECT 1 FROM booking_schema.bookings booking
                              WHERE booking.war_session_id = participant.war_session_id
                                AND booking.participant_id = participant.id
                          )
                        ORDER BY participant.competition_rank
                        """)
                .setParameter(1, warSessionId)
                .setParameter(2, warBatchId)
                .getResultList();
        return rows.stream().map(result -> {
            Object[] row = (Object[]) result;
            return new WinnerCandidate((UUID) row[0], ((Number) row[1]).intValue(),
                    (String) row[2], (String) row[3], ((Number) row[4]).intValue());
        }).toList();
    }

    public Optional<AlternateCandidate> findNextAlternate(UUID warSessionId, UUID warBatchId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT participant.id, participant.competition_rank, participant.email,
                               ws.name, batch.batch_number
                        FROM war_schema.war_participants participant
                        JOIN war_schema.war_sessions ws ON ws.id = participant.war_session_id
                        JOIN war_schema.war_batches batch ON batch.id = participant.batch_id
                        WHERE participant.war_session_id = ?
                          AND participant.batch_id = ?
                          AND participant.status = 'ALTERNATE'
                          AND NOT EXISTS (
                              SELECT 1 FROM booking_schema.bookings booking
                              WHERE booking.war_session_id = participant.war_session_id
                                AND booking.participant_id = participant.id
                          )
                        ORDER BY participant.competition_rank
                        LIMIT 1
                        """)
                .setParameter(1, warSessionId)
                .setParameter(2, warBatchId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new AlternateCandidate((UUID) row[0], ((Number) row[1]).intValue(),
                (String) row[2], (String) row[3], ((Number) row[4]).intValue()));
    }

    public Optional<NotificationRecipient> findNotificationRecipient(UUID participantId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT participant.email, ws.name
                        FROM war_schema.war_participants participant
                        JOIN war_schema.war_sessions ws ON ws.id = participant.war_session_id
                        WHERE participant.id = ?
                        """)
                .setParameter(1, participantId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new NotificationRecipient((String) row[0], (String) row[1]));
    }

    private static Integer asInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public record ParticipantContext(
            UUID participantId,
            UUID warSessionId,
            UUID warBatchId,
            String status,
            Integer batchNumber,
            Integer ticketQuota,
            boolean finalized) {
    }

    public record FinalizedBatch(UUID warSessionId, UUID warBatchId) {
    }

    public record WinnerCandidate(
            UUID participantId, int competitionRank, String email, String sessionName, int batchNumber) {
    }

    public record AlternateCandidate(
            UUID participantId, int competitionRank, String email, String sessionName, int batchNumber) {
    }

    public record NotificationRecipient(String email, String sessionName) {
    }
}
