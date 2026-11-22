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
@Table(name = "challenge_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_challenge_answers_attempt_question",
                columnNames = "attempt_question_id"
        ),
        indexes = @Index(name = "idx_challenge_answers_attempt_question_id", columnList = "attempt_question_id"))
@Getter
@Setter
@NoArgsConstructor
public class ChallengeAnswer {

    @Id
    private UUID id;

    @Column(name = "attempt_question_id", nullable = false)
    private UUID attemptQuestionId;

    @Column(name = "selected_option_id")
    private UUID selectedOptionId;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
