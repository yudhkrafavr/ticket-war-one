package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarParticipantStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminParticipantResponse(
        UUID participantId,
        String email,
        Instant registeredAt,
        Instant queueEnteredAt,
        Integer batchNumber,
        Long queuePosition,
        WarParticipantStatus status
) {
}
