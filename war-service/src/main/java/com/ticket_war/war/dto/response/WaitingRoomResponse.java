package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarParticipantStatus;

import java.time.Instant;
import java.util.UUID;

public record WaitingRoomResponse(
        UUID participantId,
        UUID warSessionId,
        UUID batchId,
        Integer batchNumber,
        Integer totalBatches,
        Long queuePosition,
        long batchParticipantCount,
        Integer participantCapacity,
        WarParticipantStatus status,
        Instant waitingRoomOpensAt,
        Instant challengeStartsAt,
        boolean challengeAvailable
) {
}
