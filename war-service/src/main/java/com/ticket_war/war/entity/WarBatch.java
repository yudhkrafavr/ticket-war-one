package com.ticket_war.war.entity;

import com.ticket_war.war.enums.WarBatchStatus;
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
@Table(name = "war_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_war_batches_session_batch_number",
                columnNames = {"war_session_id", "batch_number"}
        ),
        indexes = {
                @Index(name = "idx_war_batches_war_session_id", columnList = "war_session_id"),
                @Index(name = "idx_war_batches_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
public class WarBatch {

    @Id
    private UUID id;

    @Column(name = "war_session_id", nullable = false)
    private UUID warSessionId;

    @Column(name = "batch_number", nullable = false)
    private Integer batchNumber;

    @Column(name = "participant_capacity", nullable = false)
    private Integer participantCapacity;

    @Column(name = "ticket_quota", nullable = false)
    private Integer ticketQuota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarBatchStatus status = WarBatchStatus.PENDING;

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
