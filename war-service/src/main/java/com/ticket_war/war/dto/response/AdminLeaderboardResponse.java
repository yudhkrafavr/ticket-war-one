package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminLeaderboardResponse(
        UUID warSessionId,
        String sessionName,
        WarSessionStatus sessionStatus,
        boolean leaderboardFinalized,
        Instant leaderboardFinalizedAt,
        Integer totalParticipants,
        Integer totalBatches,
        Integer totalTickets,
        List<AdminBatchLeaderboardResponse> batches
) {
}
