package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.ChallengeAttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record ChallengeStartResponse(
        UUID attemptId,
        ChallengeAttemptStatus status,
        Instant startedAt,
        Instant deadlineAt,
        Instant serverTime,
        long remainingSeconds,
        int totalQuestions
) {
}
