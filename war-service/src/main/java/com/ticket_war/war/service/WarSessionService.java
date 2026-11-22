package com.ticket_war.war.service;

import com.ticket_war.war.dto.request.CreateWarSessionRequest;
import com.ticket_war.war.dto.request.UpdateWarSessionRequest;
import com.ticket_war.war.dto.response.EventDetailsResponse;
import com.ticket_war.war.dto.response.WarSessionResponse;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.client.EventServiceClient;
import com.ticket_war.war.repository.WarParticipantRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import com.ticket_war.war.repository.WarSessionRepository.BatchSummary;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WarSessionService {

    private final WarSessionRepository warSessionRepository;
    private final WarParticipantRepository warParticipantRepository;
    private final WarBatchService warBatchService;
    private final EventServiceClient eventServiceClient;

    public WarSessionService(
            WarSessionRepository warSessionRepository,
            WarParticipantRepository warParticipantRepository,
            WarBatchService warBatchService,
            EventServiceClient eventServiceClient) {
        this.warSessionRepository = warSessionRepository;
        this.warParticipantRepository = warParticipantRepository;
        this.warBatchService = warBatchService;
        this.eventServiceClient = eventServiceClient;
    }

    @Transactional
    public WarSessionResponse create(CreateWarSessionRequest request) {
        EventDetailsResponse event = eventServiceClient.getEvent(request.getEventId());
        validateEventCanHaveSession(event);

        Instant now = Instant.now();
        WarSession session = new WarSession();
        session.setId(UUID.randomUUID());
        session.setEventId(request.getEventId());
        session.setName(request.getName());
        session.setDescription(request.getDescription());
        session.setTotalParticipants(request.getTotalParticipants());
        session.setTotalBatches(request.getTotalBatches());
        applyEventRegistrationDetails(session, event);
        session.setQuestionsPerAttempt(request.getQuestionsPerAttempt());
        session.setChallengeDurationSeconds(request.getChallengeDurationSeconds());
        session.setWaitingRoomOpensAt(request.getWaitingRoomOpensAt());
        session.setChallengeStartsAt(request.getChallengeStartsAt());
        session.setStatus(WarSessionStatus.DRAFT);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        WarSession createdSession = warSessionRepository.insert(session);
        warBatchService.configureForSession(createdSession);
        return toResponse(createdSession);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "warSessionLists", key = "#p0 == null ? 'all' : #p0.name()")
    public List<WarSessionResponse> findAll(WarSessionStatus status) {
        List<WarSession> sessions = warSessionRepository.findAll(status);
        var registrationCounts = warParticipantRepository.countByWarSessionIds(
                sessions.stream().map(WarSession::getId).toList());
        return sessions.stream()
                .map(session -> toResponse(
                        session, registrationCounts.getOrDefault(session.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "warSessionDetails", key = "#p0")
    public WarSessionResponse findById(UUID id) {
        return toResponse(warSessionRepository.findById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public WarSessionResponse update(UUID id, UpdateWarSessionRequest request) {
        WarSession session = getDraftForUpdate(id);
        EventDetailsResponse event = eventServiceClient.getEvent(request.getEventId());
        validateEventCanHaveSession(event);
        session.setEventId(request.getEventId());
        session.setName(request.getName());
        session.setDescription(request.getDescription());
        session.setTotalParticipants(request.getTotalParticipants());
        session.setTotalBatches(request.getTotalBatches());
        applyEventRegistrationDetails(session, event);
        session.setQuestionsPerAttempt(request.getQuestionsPerAttempt());
        session.setChallengeDurationSeconds(request.getChallengeDurationSeconds());
        session.setWaitingRoomOpensAt(request.getWaitingRoomOpensAt());
        session.setChallengeStartsAt(request.getChallengeStartsAt());
        session.setUpdatedAt(Instant.now());

        if (warSessionRepository.updateDraft(session) != 1) {
            throw concurrentModification(id);
        }
        warBatchService.configureForSession(session);
        return toResponse(session);
    }

    @Transactional
    public WarSessionResponse publish(UUID id) {
        WarSession session = getDraftForUpdate(id);
        EventDetailsResponse event = eventServiceClient.getEvent(session.getEventId());
        if (!"PUBLISHED".equals(event.status())) {
            throw badRequest("The linked Event must be published before its War Session can be published.");
        }
        applyEventRegistrationDetails(session, event);
        session.setUpdatedAt(Instant.now());
        if (warSessionRepository.updateDraft(session) != 1) {
            throw concurrentModification(id);
        }
        warBatchService.configureForSession(session);
        validateSchedule(session);
        validateBatches(session);
        validateTrivia(session);

        Instant now = Instant.now();
        if (warSessionRepository.publishDraft(id, now) != 1) {
            throw concurrentModification(id);
        }

        session.setStatus(WarSessionStatus.SCHEDULED);
        session.setUpdatedAt(now);
        return toResponse(session);
    }

    private WarSession getDraftForUpdate(UUID id) {
        WarSession session = warSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound(id));
        if (session.getStatus() != WarSessionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a DRAFT War Session can be updated or published.");
        }
        return session;
    }

    private void validateSchedule(WarSession session) {
        if (session.getRegistrationOpensAt() == null
                || session.getRegistrationClosesAt() == null
                || session.getWaitingRoomOpensAt() == null
                || session.getChallengeStartsAt() == null) {
            throw badRequest("All schedule fields must be set before publishing.");
        }
        if (!session.getRegistrationOpensAt().isBefore(session.getRegistrationClosesAt())) {
            throw badRequest("registrationOpensAt must be before registrationClosesAt.");
        }
        if (session.getRegistrationClosesAt().isAfter(session.getWaitingRoomOpensAt())) {
            throw badRequest("registrationClosesAt must not be after waitingRoomOpensAt.");
        }
        if (session.getWaitingRoomOpensAt().isAfter(session.getChallengeStartsAt())) {
            throw badRequest("waitingRoomOpensAt must not be after challengeStartsAt.");
        }
    }

    private static void validateEventCanHaveSession(EventDetailsResponse event) {
        if (!"DRAFT".equals(event.status()) && !"PUBLISHED".equals(event.status())) {
            throw badRequest("War Sessions can only be linked to DRAFT or PUBLISHED Events.");
        }
        if (event.totalTickets() == null || event.totalTickets() < 1) {
            throw badRequest("The linked Event must have a positive ticket total.");
        }
    }

    private static void applyEventRegistrationDetails(WarSession session, EventDetailsResponse event) {
        session.setTotalTickets(event.totalTickets());
        session.setRegistrationOpensAt(event.registrationStartAt());
        session.setRegistrationClosesAt(event.registrationEndAt());
    }

    private void validateBatches(WarSession session) {
        BatchSummary summary = warSessionRepository.getBatchSummary(session.getId());
        if (summary.batchCount() != session.getTotalBatches()) {
            throw badRequest("Batch count must equal totalBatches (expected "
                    + session.getTotalBatches() + ", found " + summary.batchCount() + ").");
        }
        if (summary.participantCapacitySum() != session.getTotalParticipants()) {
            throw badRequest("The sum of participant_capacity must equal totalParticipants (expected "
                    + session.getTotalParticipants() + ", found "
                    + summary.participantCapacitySum() + ").");
        }
        if (summary.ticketQuotaSum() != session.getTotalTickets()) {
            throw badRequest("The sum of ticket_quota must equal totalTickets (expected "
                    + session.getTotalTickets() + ", found " + summary.ticketQuotaSum() + ").");
        }
    }

    private void validateTrivia(WarSession session) {
        long questionCount = warSessionRepository.countTriviaQuestions(session.getId());
        if (questionCount < 10) {
            throw badRequest("A War Session must have at least 10 trivia questions before publishing.");
        }
        if (questionCount < session.getQuestionsPerAttempt()) {
            throw badRequest("The trivia question count must be at least questionsPerAttempt.");
        }
        if (warSessionRepository.hasQuestionWithoutExactlyOneCorrectOption(session.getId())) {
            throw badRequest("Every trivia question must have exactly one correct option.");
        }
    }

    private WarSessionResponse toResponse(WarSession session) {
        return toResponse(session, warParticipantRepository.countByWarSessionId(session.getId()));
    }

    private static WarSessionResponse toResponse(WarSession session, long registeredParticipants) {
        return new WarSessionResponse(
                session.getId(),
                session.getEventId(),
                session.getName(),
                session.getDescription(),
                session.getTotalParticipants(),
                registeredParticipants,
                session.getTotalBatches(),
                session.getTotalTickets(),
                session.getQuestionsPerAttempt(),
                session.getChallengeDurationSeconds(),
                session.getRegistrationOpensAt(),
                session.getRegistrationClosesAt(),
                session.getWaitingRoomOpensAt(),
                session.getChallengeStartsAt(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private static ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Session not found: " + id);
    }

    private static ResponseStatusException concurrentModification(UUID id) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "War Session was modified concurrently or is no longer in DRAFT status: " + id);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
