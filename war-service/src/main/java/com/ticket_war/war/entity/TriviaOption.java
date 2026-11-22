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
@Table(name = "trivia_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trivia_options_question_option_order",
                columnNames = {"question_id", "option_order"}
        ),
        indexes = @Index(name = "idx_trivia_options_question_id", columnList = "question_id"))
@Getter
@Setter
@NoArgsConstructor
public class TriviaOption {

    @Id
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "option_text", nullable = false, length = 500)
    private String optionText;

    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

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
