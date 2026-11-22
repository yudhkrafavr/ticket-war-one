package com.ticket_war.war.dto.response;

import java.util.List;
import java.util.UUID;

public record ChallengeQuestionResponse(
        UUID attemptQuestionId,
        UUID questionId,
        String questionText,
        Integer displayOrder,
        List<ChallengeOptionResponse> options
) {
}
