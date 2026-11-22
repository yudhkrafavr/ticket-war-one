package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarParticipantStatus;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResultResponse(
        UUID participantId,
        UUID warSessionId,
        String warSessionName,
        Instant leaderboardFinalizedAt,
        Integer batchNumber,
        Long queuePosition,
        Integer competitionRank,
        Long completionDurationMs,
        WarParticipantStatus result
) {
}
