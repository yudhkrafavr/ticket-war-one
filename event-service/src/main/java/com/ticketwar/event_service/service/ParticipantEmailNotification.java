package com.ticketwar.event_service.service;

import java.time.Instant;

public record ParticipantEmailNotification(
        Kind kind,
        String recipient,
        String sessionName,
        String token,
        Integer batchNumber,
        Integer ticketNumber,
        Instant expiresAt) {

    public enum Kind {
        BOOKING_INVITATION,
        TICKET_CLAIMED
    }
}
