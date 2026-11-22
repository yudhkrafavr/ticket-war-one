package com.ticket_war.war.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "trivia_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trivia_questions_session_question_order",
                columnNames = {"war_session_id", "question_order"}
        ),
        indexes = @Index(name = "idx_trivia_questions_session_id", columnList = "war_session_id"))
@Getter
@Setter
@NoArgsConstructor
public class TriviaQuestion {

    @Id
    private UUID id;

    @Column(name = "war_session_id", nullable = false)
    private UUID warSessionId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

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
