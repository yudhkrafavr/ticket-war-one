package com.ticketwar.event_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings", schema = "booking_schema",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookings_session_participant",
                columnNames = {"war_session_id", "participant_id"}),
        indexes = {
                @Index(name = "idx_bookings_batch_status", columnList = "war_session_id, war_batch_id, status"),
                @Index(name = "idx_bookings_participant_invited", columnList = "participant_id, invited_at"),
                @Index(name = "idx_bookings_expiry", columnList = "status, expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class BookingEntity {

    @Id
    private UUID id;

    @Column(name = "war_session_id", nullable = false)
    private UUID warSessionId;

    @Column(name = "war_batch_id", nullable = false)
    private UUID warBatchId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "ticket_number", nullable = false)
    private Integer ticketNumber;

    @Column(name = "email_token_hash", length = 64)
    private String emailTokenHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
