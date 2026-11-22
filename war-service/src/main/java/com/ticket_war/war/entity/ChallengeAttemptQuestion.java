package com.ticket_war.war.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_attempt_questions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_attempt_questions_attempt_question",
                        columnNames = {"attempt_id", "question_id"}
                ),
                @UniqueConstraint(
                        name = "uk_attempt_questions_attempt_display_order",
                        columnNames = {"attempt_id", "display_order"}
                )
        },
        indexes = {
                @Index(name = "idx_attempt_questions_attempt_id", columnList = "attempt_id"),
                @Index(name = "idx_attempt_questions_question_id", columnList = "question_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ChallengeAttemptQuestion {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
