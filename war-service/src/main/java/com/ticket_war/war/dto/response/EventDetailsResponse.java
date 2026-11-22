package com.ticket_war.war.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EventDetailsResponse(
        UUID id,
        String name,
        String description,
        String venue,
        Instant eventDate,
        Integer totalTickets,
        Instant registrationStartAt,
        Instant registrationEndAt,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
