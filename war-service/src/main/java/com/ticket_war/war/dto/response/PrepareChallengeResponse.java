package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.ChallengeAttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record PrepareChallengeResponse(
        UUID attemptId,
        UUID participantId,
        ChallengeAttemptStatus status,
        Instant scheduledStartAt,
        Instant deadlineAt,
        Integer durationSeconds,
        int totalQuestions,
        boolean questionsPrepared
) {
}
