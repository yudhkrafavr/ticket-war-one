package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.ChallengeAttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChallengeResponse(
        UUID attemptId,
        Instant startedAt,
        Instant deadlineAt,
        Instant serverTime,
        long remainingSeconds,
        ChallengeAttemptStatus status,
        List<ChallengeQuestionResponse> questions
) {
}
