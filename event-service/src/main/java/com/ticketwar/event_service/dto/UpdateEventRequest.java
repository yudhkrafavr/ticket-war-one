package com.ticketwar.event_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateEventRequest(
        @Size(max = 150)
        String name,
        String description,
        @Size(max = 255)
        String venue,
        @NotNull
        Instant eventDate,
        Integer totalTickets,
        Instant registrationStartAt,
        Instant registrationEndAt
) {
}
