package com.ticket_war.war.service;

import com.ticket_war.war.dto.request.ChallengeAnswerSubmission;
import com.ticket_war.war.dto.request.SubmitChallengeRequest;
import com.ticket_war.war.dto.response.ChallengeOptionResponse;
import com.ticket_war.war.dto.response.ChallengeQuestionResponse;
import com.ticket_war.war.dto.response.ChallengeResponse;
import com.ticket_war.war.dto.response.ChallengeStartResponse;
import com.ticket_war.war.dto.response.ChallengeSubmissionResponse;
import com.ticket_war.war.dto.response.PrepareChallengeResponse;
import com.ticket_war.war.entity.ChallengeAnswer;
import com.ticket_war.war.entity.ChallengeAttempt;
import com.ticket_war.war.entity.ChallengeAttemptQuestion;
import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.entity.WarParticipant;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.ChallengeAttemptStatus;
import com.ticket_war.war.enums.WarBatchStatus;
import com.ticket_war.war.enums.WarParticipantStatus;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.repository.ChallengeRepository;
import com.ticket_war.war.repository.ChallengeRepository.AssignedQuestion;
import com.ticket_war.war.repository.ChallengeRepository.AttemptQuestionAssignment;
import com.ticket_war.war.repository.ChallengeRepository.ChallengeOption;
import com.ticket_war.war.repository.ChallengeRepository.QuestionOptionStats;
import com.ticket_war.war.repository.TriviaQuestionRepository;
import com.ticket_war.war.repository.WarBatchRepository;
import com.ticket_war.war.repository.WarParticipantRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChallengeService {

    private final WarParticipantService warParticipantService;
    private final WarParticipantRepository warParticipantRepository;
    private final WarSessionRepository warSessionRepository;
    private final WarBatchRepository warBatchRepository;
    private final TriviaQuestionRepository triviaQuestionRepository;
    private final ChallengeRepository challengeRepository;

    public ChallengeService(
            WarParticipantService warParticipantService,
            WarParticipantRepository warParticipantRepository,
            WarSessionRepository warSessionRepository,
            WarBatchRepository warBatchRepository,
            TriviaQuestionRepository triviaQuestionRepository,
            ChallengeRepository challengeRepository) {
        this.warParticipantService = warParticipantService;
        this.warParticipantRepository = warParticipantRepository;
        this.warSessionRepository = warSessionRepository;
        this.warBatchRepository = warBatchRepository;
        this.triviaQuestionRepository = triviaQuestionRepository;
        this.challengeRepository = challengeRepository;
    }

    @Transactional
    public PrepareResult prepare(String accessToken) {
        WarParticipant tokenParticipant =
                warParticipantService.getAuthenticatedParticipant(accessToken);
        WarSession session = warSessionRepository.findByIdForUpdate(tokenParticipant.getWarSessionId())
                .orElseThrow(() -> sessionNotFound(tokenParticipant.getWarSessionId()));
        WarParticipant participant = warParticipantRepository.findByIdForUpdate(tokenParticipant.getId())
                .orElseThrow(ChallengeService::unauthorized);

        Optional<ChallengeAttempt> existingAttempt =
                challengeRepository.findAttemptByParticipantId(participant.getId());
        if (existingAttempt.isPresent()) {
            ChallengeAttempt attempt = existingAttempt.get();
            if (attempt.getStatus() == ChallengeAttemptStatus.READY
                    || attempt.getStatus() == ChallengeAttemptStatus.IN_PROGRESS) {
                return new PrepareResult(prepareResponse(attempt, session), false);
            }
            throw conflict("A participant can have only one challenge attempt.");
        }

        if (participant.getBatchId() == null) {
            throw conflict("Participant must enter the waiting room before preparing the challenge.");
        }
        if (participant.getStatus() != WarParticipantStatus.WAITING) {
            throw conflict("Participant must be WAITING before preparing the challenge.");
        }
        warBatchRepository.findByIdAndWarSessionId(participant.getBatchId(), session.getId())
                .orElseThrow(() -> conflict("Participant batch does not belong to the War Session."));
        validateSessionCanBePrepared(session);

        int questionCount = session.getQuestionsPerAttempt();
        List<UUID> selectedQuestionIds = challengeRepository.selectRandomQuestionIds(
                session.getId(), questionCount);
        if (selectedQuestionIds.size() != questionCount) {
            throw conflict("The War Session does not have enough valid trivia questions for this challenge.");
        }
        for (UUID questionId : selectedQuestionIds) {
            QuestionOptionStats optionStats = challengeRepository.getQuestionOptionStats(questionId);
            if (optionStats.optionCount() < 2 || optionStats.correctOptionCount() != 1) {
                throw conflict("A selected trivia question must have at least two options and exactly one correct option.");
            }
        }

        Instant preparedAt = Instant.now();
        if (!preparedAt.isBefore(session.getChallengeStartsAt())) {
            throw conflict("Challenge preparation must finish before the scheduled start time.");
        }
        Instant deadlineAt = sharedDeadline(session);
        ChallengeAttempt attempt = new ChallengeAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setParticipantId(participant.getId());
        attempt.setStartedAt(session.getChallengeStartsAt());
        attempt.setDeadlineAt(deadlineAt);
        attempt.setSubmittedAt(null);
        attempt.setCompletionDurationMs(null);
        attempt.setAllAnswersCorrect(null);
        attempt.setAutoSubmitted(false);
        attempt.setStatus(ChallengeAttemptStatus.READY);
        attempt.setCreatedAt(preparedAt);
        attempt.setUpdatedAt(preparedAt);

        try {
            attempt = challengeRepository.insertAttempt(attempt);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A challenge attempt already exists for this participant.");
        }

        for (int index = 0; index < selectedQuestionIds.size(); index++) {
            ChallengeAttemptQuestion attemptQuestion = new ChallengeAttemptQuestion();
            attemptQuestion.setId(UUID.randomUUID());
            attemptQuestion.setAttemptId(attempt.getId());
            attemptQuestion.setQuestionId(selectedQuestionIds.get(index));
            attemptQuestion.setDisplayOrder(index + 1);
            attemptQuestion.setCreatedAt(preparedAt);
            if (challengeRepository.insertAttemptQuestion(attemptQuestion) != 1) {
                throw new IllegalStateException("Selected trivia question could not be assigned.");
            }
        }

        return new PrepareResult(prepareResponse(attempt, session), true);
    }

    @Transactional
    @CacheEvict(cacheNames = {
            "participantWaitingRooms", "warSessionLists", "warSessionDetails",
            "warBatchLists", "warBatchDetails"
    }, allEntries = true)
    public ChallengeStartResponse start(String accessToken) {
        WarParticipant authenticatedParticipant =
                warParticipantService.getAuthenticatedParticipant(accessToken);
        WarSession session = warSessionRepository.findByIdForUpdate(
                        authenticatedParticipant.getWarSessionId())
                .orElseThrow(() -> sessionNotFound(authenticatedParticipant.getWarSessionId()));
        ChallengeAttempt attempt = challengeRepository
                .findAttemptByParticipantIdForUpdate(authenticatedParticipant.getId())
                .orElseThrow(() -> attemptNotFoundForParticipant());
        WarParticipant participant = warParticipantRepository
                .findByIdForUpdate(authenticatedParticipant.getId())
                .orElseThrow(ChallengeService::unauthorized);

        if (attempt.getStatus() != ChallengeAttemptStatus.READY
                && attempt.getStatus() != ChallengeAttemptStatus.IN_PROGRESS) {
            throw conflict("Challenge attempt is not ready to start.");
        }
        validateSessionNotTerminal(session);
        Instant deadlineAt = sharedDeadline(session);
        Instant currentTime = Instant.now();
        if (currentTime.isBefore(session.getChallengeStartsAt())) {
            throw conflict("The scheduled challenge start time has not arrived.");
        }
        if (currentTime.isAfter(deadlineAt)) {
            throw conflict("The shared challenge deadline has passed.");
        }
        if (attempt.getStatus() == ChallengeAttemptStatus.READY) {
            if (participant.getBatchId() == null) {
                throw conflict("Participant is not assigned to a batch.");
            }
            WarBatch batch = warBatchRepository.findByIdAndWarSessionId(
                            participant.getBatchId(), session.getId())
                    .orElseThrow(() -> conflict("Participant batch does not belong to the War Session."));
            if (participant.getStatus() != WarParticipantStatus.WAITING) {
                throw conflict("Participant state is not ready to start the challenge.");
            }
            if (batch.getStatus() != WarBatchStatus.WAITING_ROOM_OPEN
                    && batch.getStatus() != WarBatchStatus.IN_PROGRESS) {
                throw conflict("Participant batch is not ready to start the challenge.");
            }

            Instant updatedAt = Instant.now();
            if (challengeRepository.activateReadyAttempt(attempt.getId(), updatedAt) != 1) {
                throw conflict("Challenge attempt state changed while starting.");
            }
            if (warParticipantRepository.updateStatus(
                    participant.getId(),
                    WarParticipantStatus.WAITING,
                    WarParticipantStatus.CHALLENGE_IN_PROGRESS,
                    updatedAt) != 1) {
                throw conflict("Participant state changed while starting the challenge.");
            }

            ensureSessionInProgress(session, updatedAt);
            ensureBatchInProgress(batch, updatedAt);
            attempt.setStatus(ChallengeAttemptStatus.IN_PROGRESS);
            attempt.setUpdatedAt(updatedAt);
        } else if (participant.getStatus() != WarParticipantStatus.CHALLENGE_IN_PROGRESS) {
            throw conflict("Participant state does not match the active challenge attempt.");
        }

        Instant serverTime = Instant.now();
        return new ChallengeStartResponse(
                attempt.getId(),
                attempt.getStatus(),
                session.getChallengeStartsAt(),
                deadlineAt,
                serverTime,
                remainingSeconds(deadlineAt, serverTime),
                Math.toIntExact(challengeRepository.countAttemptQuestions(attempt.getId())));
    }

    @Transactional(readOnly = true)
    public ChallengeResponse getChallenge(String accessToken, UUID attemptId) {
        WarParticipant participant = warParticipantService.getAuthenticatedParticipant(accessToken);
        ChallengeAttempt attempt = challengeRepository
                .findAttemptByIdAndParticipantId(attemptId, participant.getId())
                .orElseThrow(() -> attemptNotFound(attemptId));
        WarSession session = warSessionRepository.findById(participant.getWarSessionId())
                .orElseThrow(() -> sessionNotFound(participant.getWarSessionId()));
        if (session.getChallengeStartsAt() == null) {
            throw conflict("The War Session challenge start time is not configured.");
        }

        Instant serverTime = Instant.now();
        if (serverTime.isBefore(session.getChallengeStartsAt())) {
            throw conflict("Challenge questions are not available before the scheduled start time.");
        }
        if (attempt.getStatus() == ChallengeAttemptStatus.READY) {
            throw conflict("Activate the prepared challenge using the start endpoint before retrieving questions.");
        }
        if (attempt.getStatus() != ChallengeAttemptStatus.IN_PROGRESS
                && attempt.getStatus() != ChallengeAttemptStatus.SUBMITTED) {
            throw conflict("Challenge questions are not available for this attempt.");
        }

        Instant deadlineAt = sharedDeadline(session);
        List<AssignedQuestion> assignedQuestions = challengeRepository.findAssignedQuestions(attemptId);
        List<UUID> questionIds = assignedQuestions.stream().map(AssignedQuestion::questionId).toList();
        Map<UUID, List<ChallengeOption>> optionsByQuestion =
                challengeRepository.findOptionsByQuestionIds(questionIds).stream()
                        .collect(Collectors.groupingBy(ChallengeOption::questionId));

        List<ChallengeQuestionResponse> questions = assignedQuestions.stream()
                .map(question -> new ChallengeQuestionResponse(
                        question.attemptQuestionId(),
                        question.questionId(),
                        question.questionText(),
                        question.displayOrder(),
                        optionsByQuestion.getOrDefault(question.questionId(), List.of()).stream()
                                .map(option -> new ChallengeOptionResponse(
                                        option.id(), option.optionText(), option.optionOrder()))
                                .toList()))
                .toList();

        return new ChallengeResponse(
                attempt.getId(),
                session.getChallengeStartsAt(),
                deadlineAt,
                serverTime,
                remainingSeconds(deadlineAt, serverTime),
                attempt.getStatus(),
                questions);
    }

    @Transactional
    @CacheEvict(cacheNames = "participantWaitingRooms", allEntries = true)
    public ChallengeSubmissionResponse submit(
            String accessToken,
            UUID attemptId,
            SubmitChallengeRequest request) {
        WarParticipant participant = warParticipantService.getAuthenticatedParticipant(accessToken);
        ChallengeAttempt attempt = challengeRepository
                .findAttemptByIdAndParticipantIdForUpdate(attemptId, participant.getId())
                .orElseThrow(() -> attemptNotFound(attemptId));
        if (attempt.getStatus() != ChallengeAttemptStatus.IN_PROGRESS) {
            throw conflict("This challenge attempt has already been submitted or is no longer active.");
        }

        List<AttemptQuestionAssignment> assignments =
                challengeRepository.findAttemptQuestionAssignments(attemptId);
        Map<UUID, AttemptQuestionAssignment> assignmentsById = new HashMap<>();
        for (AttemptQuestionAssignment assignment : assignments) {
            assignmentsById.put(assignment.attemptQuestionId(), assignment);
        }
        if (assignments.isEmpty()) {
            throw conflict("This challenge attempt has no assigned questions.");
        }

        List<ChallengeAnswerSubmission> suppliedAnswers = request == null || request.getAnswers() == null
                ? List.of()
                : request.getAnswers();
        Map<UUID, UUID> selectedOptionByAttemptQuestion = new HashMap<>();
        Set<UUID> suppliedQuestionIds = new HashSet<>();
        for (ChallengeAnswerSubmission answer : suppliedAnswers) {
            if (answer == null || answer.getAttemptQuestionId() == null || answer.getSelectedOptionId() == null) {
                throw badRequest("Each submitted answer requires an attemptQuestionId and selectedOptionId.");
            }
            UUID attemptQuestionId = answer.getAttemptQuestionId();
            if (!suppliedQuestionIds.add(attemptQuestionId)) {
                throw badRequest("Duplicate attemptQuestionId values are not allowed.");
            }
            AttemptQuestionAssignment assignment = assignmentsById.get(attemptQuestionId);
            if (assignment == null) {
                throw badRequest("Submitted question was not assigned to this attempt.");
            }
            if (!challengeRepository.optionBelongsToQuestion(
                    answer.getSelectedOptionId(), assignment.questionId())) {
                throw badRequest("Selected option does not belong to its assigned question.");
            }
            selectedOptionByAttemptQuestion.put(attemptQuestionId, answer.getSelectedOptionId());
        }

        Map<UUID, UUID> correctOptionByQuestion = new HashMap<>();
        for (AttemptQuestionAssignment assignment : assignments) {
            UUID correctOptionId = challengeRepository.findCorrectOptionId(assignment.questionId())
                    .orElseThrow(() -> conflict("An assigned question no longer has a correct option."));
            correctOptionByQuestion.put(assignment.questionId(), correctOptionId);
        }

        WarSession session = warSessionRepository.findById(participant.getWarSessionId())
                .orElseThrow(() -> sessionNotFound(participant.getWarSessionId()));
        if (session.getChallengeStartsAt() == null) {
            throw conflict("The War Session challenge start time is not configured.");
        }
        Instant submittedAt = Instant.now();
        Instant sharedDeadline = sharedDeadline(session);
        long configuredDurationMs = session.getChallengeDurationSeconds() * 1_000L;
        long elapsedMs = Math.max(0,
                Duration.between(session.getChallengeStartsAt(), submittedAt).toMillis());
        long completionDurationMs = Math.min(elapsedMs, configuredDurationMs);
        boolean autoSubmitted = !submittedAt.isBefore(sharedDeadline);

        int correctAnswers = 0;
        int incorrectAnswers = 0;
        int unansweredQuestions = 0;
        for (AttemptQuestionAssignment assignment : assignments) {
            UUID selectedOptionId = selectedOptionByAttemptQuestion.get(assignment.attemptQuestionId());
            boolean isCorrect = selectedOptionId != null
                    && selectedOptionId.equals(correctOptionByQuestion.get(assignment.questionId()));
            if (selectedOptionId == null) {
                unansweredQuestions++;
            } else if (isCorrect) {
                correctAnswers++;
            } else {
                incorrectAnswers++;
            }

            ChallengeAnswer answer = new ChallengeAnswer();
            answer.setId(UUID.randomUUID());
            answer.setAttemptQuestionId(assignment.attemptQuestionId());
            answer.setSelectedOptionId(selectedOptionId);
            answer.setIsCorrect(isCorrect);
            answer.setCreatedAt(submittedAt);
            if (challengeRepository.insertAnswer(answer) != 1) {
                throw new IllegalStateException("Challenge answer could not be saved.");
            }
        }

        boolean allAnswersCorrect = correctAnswers == assignments.size();
        attempt.setSubmittedAt(submittedAt);
        attempt.setCompletionDurationMs(completionDurationMs);
        attempt.setAllAnswersCorrect(allAnswersCorrect);
        attempt.setAutoSubmitted(autoSubmitted);
        attempt.setStatus(ChallengeAttemptStatus.SUBMITTED);
        attempt.setUpdatedAt(submittedAt);
        if (challengeRepository.updateSubmittedAttempt(attempt) != 1) {
            throw conflict("Challenge attempt state changed during submission.");
        }
        if (warParticipantRepository.updateStatus(
                participant.getId(),
                WarParticipantStatus.CHALLENGE_IN_PROGRESS,
                WarParticipantStatus.COMPLETED,
                submittedAt) != 1) {
            throw conflict("Participant state changed during challenge submission.");
        }

        return new ChallengeSubmissionResponse(
                attempt.getId(),
                participant.getId(),
                attempt.getStatus(),
                assignments.size(),
                correctAnswers,
                incorrectAnswers,
                unansweredQuestions,
                allAnswersCorrect,
                completionDurationMs,
                submittedAt,
                autoSubmitted);
    }

    private void validateSessionCanBePrepared(WarSession session) {
        validateSessionNotTerminal(session);
        if (session.getChallengeStartsAt() == null || session.getChallengeDurationSeconds() == null
                || session.getChallengeDurationSeconds() <= 0
                || session.getQuestionsPerAttempt() == null || session.getQuestionsPerAttempt() <= 0) {
            throw conflict("The War Session challenge schedule or duration is not configured.");
        }
        if (!Instant.now().isBefore(session.getChallengeStartsAt())) {
            throw conflict("Challenge preparation must happen before the scheduled start time.");
        }
    }

    private static void validateSessionNotTerminal(WarSession session) {
        if (session.getStatus() == WarSessionStatus.CANCELLED
                || session.getStatus() == WarSessionStatus.COMPLETED) {
            throw conflict("Challenge operations are unavailable for this War Session.");
        }
    }

    private void ensureSessionInProgress(WarSession session, Instant updatedAt) {
        if (session.getStatus() == WarSessionStatus.IN_PROGRESS) {
            return;
        }
        if (session.getStatus() == WarSessionStatus.CANCELLED
                || session.getStatus() == WarSessionStatus.COMPLETED) {
            throw conflict("War Session is no longer eligible for challenge start.");
        }
        if (warSessionRepository.markInProgress(session.getId(), updatedAt) != 1) {
            WarSession current = warSessionRepository.findById(session.getId())
                    .orElseThrow(() -> sessionNotFound(session.getId()));
            if (current.getStatus() != WarSessionStatus.IN_PROGRESS) {
                throw conflict("War Session status changed while starting the challenge.");
            }
        }
    }

    private void ensureBatchInProgress(WarBatch batch, Instant updatedAt) {
        if (batch.getStatus() == WarBatchStatus.IN_PROGRESS) {
            return;
        }
        if (batch.getStatus() != WarBatchStatus.WAITING_ROOM_OPEN) {
            throw conflict("Batch status changed while starting the challenge.");
        }
        if (warBatchRepository.markInProgress(batch.getId(), batch.getWarSessionId(), updatedAt) != 1) {
            WarBatch current = warBatchRepository.findByIdAndWarSessionId(
                            batch.getId(), batch.getWarSessionId())
                    .orElseThrow(() -> conflict("Participant batch no longer exists."));
            if (current.getStatus() != WarBatchStatus.IN_PROGRESS) {
                throw conflict("Batch status changed while starting the challenge.");
            }
        }
    }

    private PrepareChallengeResponse prepareResponse(
            ChallengeAttempt attempt,
            WarSession session) {
        return new PrepareChallengeResponse(
                attempt.getId(),
                attempt.getParticipantId(),
                attempt.getStatus(),
                session.getChallengeStartsAt(),
                sharedDeadline(session),
                session.getChallengeDurationSeconds(),
                Math.toIntExact(challengeRepository.countAttemptQuestions(attempt.getId())),
                true);
    }

    private static Instant sharedDeadline(WarSession session) {
        if (session.getChallengeStartsAt() == null || session.getChallengeDurationSeconds() == null
                || session.getChallengeDurationSeconds() <= 0) {
            throw conflict("The War Session challenge schedule or duration is not configured.");
        }
        return session.getChallengeStartsAt().plusSeconds(session.getChallengeDurationSeconds());
    }

    private static long remainingSeconds(Instant deadlineAt, Instant serverTime) {
        return Math.max(0, Duration.between(serverTime, deadlineAt).getSeconds());
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Participant access token is invalid.");
    }

    private static ResponseStatusException attemptNotFoundForParticipant() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No prepared challenge attempt was found for this participant.");
    }

    private static ResponseStatusException attemptNotFound(UUID attemptId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Challenge attempt not found: " + attemptId);
    }

    private static ResponseStatusException sessionNotFound(UUID sessionId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Session not found: " + sessionId);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record PrepareResult(PrepareChallengeResponse response, boolean created) {
    }
}
