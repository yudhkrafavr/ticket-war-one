package com.ticketwar.event_service.dto.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingStatusResponse(
        String status,
        UUID warSessionId,
        Integer batchNumber,
        Integer ticketNumber,
        Instant expiresAt,
        String message
) {
}
