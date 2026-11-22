package com.ticket_war.war.entity;

import com.ticket_war.war.enums.ChallengeAttemptStatus;
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
@Table(name = "challenge_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_challenge_attempts_participant_id",
                columnNames = "participant_id"
        ),
        indexes = {
                @Index(name = "idx_challenge_attempts_participant_id", columnList = "participant_id"),
                @Index(name = "idx_challenge_attempts_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
public class ChallengeAttempt {

    @Id
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completion_duration_ms")
    private Long completionDurationMs;

    @Column(name = "all_answers_correct")
    private Boolean allAnswersCorrect;

    @Column(name = "auto_submitted", nullable = false)
    private Boolean autoSubmitted = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChallengeAttemptStatus status = ChallengeAttemptStatus.READY;

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
