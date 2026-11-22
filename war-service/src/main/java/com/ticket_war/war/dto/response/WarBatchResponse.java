package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarBatchStatus;

import java.time.Instant;
import java.util.UUID;

public record WarBatchResponse(
        UUID id,
        UUID warSessionId,
        Integer batchNumber,
        Integer participantCapacity,
        Integer ticketQuota,
        WarBatchStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
