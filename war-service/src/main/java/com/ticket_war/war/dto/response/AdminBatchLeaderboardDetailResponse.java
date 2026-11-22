package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.WarSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminBatchLeaderboardDetailResponse(
        UUID warSessionId,
        String sessionName,
        WarSessionStatus sessionStatus,
        boolean leaderboardFinalized,
        Instant leaderboardFinalizedAt,
        Integer totalParticipants,
        Integer totalBatches,
        Integer totalTickets,
        AdminBatchLeaderboardResponse batch
) {
}
