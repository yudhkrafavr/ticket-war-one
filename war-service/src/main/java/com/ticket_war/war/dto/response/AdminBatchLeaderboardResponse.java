package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarBatchStatus;

import java.util.List;
import java.util.UUID;

public record AdminBatchLeaderboardResponse(
        UUID batchId,
        Integer batchNumber,
        WarBatchStatus batchStatus,
        Integer ticketQuota,
        Integer participantCapacity,
        long totalParticipants,
        long qualifiedParticipants,
        long winnerCount,
        long alternateCount,
        long lostCount,
        List<AdminLeaderboardParticipantResponse> participants
) {
}
