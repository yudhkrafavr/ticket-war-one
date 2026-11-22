package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarParticipantStatus;

import java.util.UUID;

public record ParticipantAccessValidationResponse(
        boolean valid,
        UUID participantId,
        UUID warSessionId,
        String email,
        WarParticipantStatus status
) {
}
