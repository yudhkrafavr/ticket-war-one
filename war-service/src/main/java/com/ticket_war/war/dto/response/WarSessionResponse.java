package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record WarSessionResponse(
        UUID id,
        UUID eventId,
        String name,
        String description,
        Integer totalParticipants,
        long registeredParticipants,
        Integer totalBatches,
        Integer totalTickets,
        Integer questionsPerAttempt,
        Integer challengeDurationSeconds,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant waitingRoomOpensAt,
        Instant challengeStartsAt,
        WarSessionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
