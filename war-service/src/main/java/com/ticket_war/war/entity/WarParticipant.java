package com.ticket_war.war.entity;

import com.ticket_war.war.enums.WarParticipantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "war_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_war_participants_session_email",
                columnNames = {"war_session_id", "email"}
        ),
        indexes = {
                @Index(name = "idx_war_participants_session_id", columnList = "war_session_id"),
                @Index(name = "idx_war_participants_batch_id", columnList = "batch_id"),
                @Index(name = "idx_war_participants_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
public class WarParticipant {

    @Id
    private UUID id;

    @Column(name = "war_session_id", nullable = false)
    private UUID warSessionId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "access_token_hash", nullable = false)
    private String accessTokenHash;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "queue_entered_at")
    private Instant queueEnteredAt;

    @Column(name = "queue_position")
    private Long queuePosition;

    @Column(name = "competition_rank")
    private Integer competitionRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarParticipantStatus status = WarParticipantStatus.PREREGISTERED;

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
