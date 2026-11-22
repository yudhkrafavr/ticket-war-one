package com.ticket_war.war.dto.response;

import java.util.UUID;

public record TriviaOptionResponse(
        UUID id,
        String optionText,
        Integer optionOrder,
        Boolean isCorrect
) {
}
