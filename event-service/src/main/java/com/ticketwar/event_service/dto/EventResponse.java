package com.ticketwar.event_service.dto;

import com.ticketwar.event_service.enums.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String description,
        String venue,
        Instant eventDate,
        Integer totalTickets,
        Instant registrationStartAt,
        Instant registrationEndAt,
        EventStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
