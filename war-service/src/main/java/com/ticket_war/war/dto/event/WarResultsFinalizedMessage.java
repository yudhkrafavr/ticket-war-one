package com.ticket_war.war.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WarResultsFinalizedMessage(
        UUID eventId,
        String sessionName,
        UUID eventMessageId,
        String eventType,
        int eventVersion,
        UUID warSessionId,
        Instant finalizedAt,
        List<BatchResults> batches
) {
    public record BatchResults(
            UUID batchId,
            int batchNumber,
            int ticketQuota,
            List<ParticipantResult> winners,
            List<ParticipantResult> alternates
    ) {
    }

    public record ParticipantResult(
            UUID participantId,
            String email,
            int competitionRank
    ) {
    }
}
