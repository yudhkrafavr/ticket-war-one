package com.ticket_war.war.entity;

import com.ticket_war.war.enums.WarSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "war_sessions", indexes = {
        @Index(name = "idx_war_sessions_event_id", columnList = "event_id"),
        @Index(name = "idx_war_sessions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class WarSession {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_participants", nullable = false)
    private Integer totalParticipants;

    @Column(name = "total_batches", nullable = false)
    private Integer totalBatches;

    @Column(name = "total_tickets", nullable = false)
    private Integer totalTickets;

    @Column(name = "questions_per_attempt", nullable = false)
    private Integer questionsPerAttempt;

    @Column(name = "challenge_duration_seconds", nullable = false)
    private Integer challengeDurationSeconds;

    @Column(name = "registration_opens_at")
    private Instant registrationOpensAt;

    @Column(name = "registration_closes_at")
    private Instant registrationClosesAt;

    @Column(name = "waiting_room_opens_at")
    private Instant waitingRoomOpensAt;

    @Column(name = "challenge_starts_at")
    private Instant challengeStartsAt;

    @Column(name = "leaderboard_finalized", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean leaderboardFinalized = false;

    @Column(name = "leaderboard_finalized_at")
    private Instant leaderboardFinalizedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarSessionStatus status = WarSessionStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
