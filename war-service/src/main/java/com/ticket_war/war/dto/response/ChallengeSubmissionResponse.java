package com.ticket_war.war.dto.response;

import com.ticket_war.war.enums.ChallengeAttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record ChallengeSubmissionResponse(
        UUID attemptId,
        UUID participantId,
        ChallengeAttemptStatus status,
        int totalQuestions,
        int correctAnswers,
        int incorrectAnswers,
        int unansweredQuestions,
        boolean allAnswersCorrect,
        long completionDurationMs,
        Instant submittedAt,
        boolean autoSubmitted
) {
}
