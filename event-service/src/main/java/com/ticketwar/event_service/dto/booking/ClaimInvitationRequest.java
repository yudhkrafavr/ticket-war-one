package com.ticketwar.event_service.dto.booking;

import jakarta.validation.constraints.NotBlank;

public record ClaimInvitationRequest(@NotBlank String token) {
}
