package com.ticket_war.war.dto.response;

import java.util.UUID;

public record ChallengeOptionResponse(
        UUID id,
        String optionText,
        Integer optionOrder
) {
}
