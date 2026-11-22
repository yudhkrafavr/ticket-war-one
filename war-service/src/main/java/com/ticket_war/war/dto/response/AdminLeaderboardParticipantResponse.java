package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarParticipantStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminLeaderboardParticipantResponse(
        UUID participantId,
        String email,
        Integer competitionRank,
        Long queuePosition,
        Instant queueEnteredAt,
        Long completionDurationMs,
        WarParticipantStatus competitionResult
) {
}
