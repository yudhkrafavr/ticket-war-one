package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarParticipantStatus;

import java.time.Instant;
import java.util.UUID;

public record RegisterParticipantResponse(
        UUID participantId,
        UUID warSessionId,
        String email,
        Instant registeredAt,
        WarParticipantStatus status,
        String accessToken
) {
}
