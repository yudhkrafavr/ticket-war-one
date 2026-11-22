package com.ticket_war.war.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TriviaQuestionResponse(
        UUID id,
        UUID warSessionId,
        String questionText,
        Integer questionOrder,
        List<TriviaOptionResponse> options,
        Instant createdAt,
        Instant updatedAt
) {
}
