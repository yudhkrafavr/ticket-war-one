package com.ticket_war.war.service;

import com.ticket_war.war.dto.response.WaitingRoomResponse;
import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.entity.WarParticipant;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarBatchStatus;
import com.ticket_war.war.enums.WarParticipantStatus;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.repository.WarBatchRepository;
import com.ticket_war.war.repository.WarParticipantRepository;
import com.ticket_war.war.repository.WarParticipantRepository.BatchEntryStats;
import com.ticket_war.war.repository.WarSessionRepository;
import com.ticket_war.war.util.ParticipantAccessTokenUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WaitingRoomService {

    private final WarParticipantService warParticipantService;
    private final WarSessionRepository warSessionRepository;
    private final WarBatchRepository warBatchRepository;
    private final WarParticipantRepository warParticipantRepository;

    public WaitingRoomService(
            WarParticipantService warParticipantService,
            WarSessionRepository warSessionRepository,
            WarBatchRepository warBatchRepository,
            WarParticipantRepository warParticipantRepository) {
        this.warParticipantService = warParticipantService;
        this.warSessionRepository = warSessionRepository;
        this.warBatchRepository = warBatchRepository;
        this.warParticipantRepository = warParticipantRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {
            "participantWaitingRooms", "warSessionLists", "warSessionDetails",
            "warBatchLists", "warBatchDetails"
    }, allEntries = true)
    public WaitingRoomResponse enter(String accessToken) {
        WarParticipant authenticatedParticipant =
                warParticipantService.getAuthenticatedParticipant(accessToken);
        UUID warSessionId = authenticatedParticipant.getWarSessionId();
        WarSession session = warSessionRepository.findByIdForUpdate(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));

        WarParticipant participant = warParticipantService.getAuthenticatedParticipant(accessToken);
        if (!warSessionId.equals(participant.getWarSessionId())) {
            throw conflict("Participant session changed during waiting-room entry.");
        }

        if (participant.getBatchId() != null && participant.getQueueEnteredAt() != null) {
            return assignedResponse(session, participant, Instant.now());
        }
        if (participant.getBatchId() != null || participant.getQueueEnteredAt() != null) {
            throw conflict("Participant waiting-room assignment is incomplete.");
        }
        if (participant.getStatus() != WarParticipantStatus.PREREGISTERED) {
            throw conflict("Participant is not in a state that can enter the waiting room.");
        }
        validateSessionCanBeEntered(session);

        List<WarBatch> batches = warBatchRepository.findAllForUpdateByWarSessionId(warSessionId);
        WarBatch selectedBatch = null;
        long nextQueuePosition = 0;
        for (WarBatch batch : batches) {
            if (batch.getStatus() != WarBatchStatus.PENDING
                    && batch.getStatus() != WarBatchStatus.WAITING_ROOM_OPEN) {
                continue;
            }

            BatchEntryStats stats = warParticipantRepository.getBatchEntryStats(batch.getId());
            if (stats.enteredCount() < batch.getParticipantCapacity()) {
                selectedBatch = batch;
                nextQueuePosition = stats.maxQueuePosition() + 1;
                break;
            }
        }
        if (selectedBatch == null) {
            throw conflict("All War Session batches are at participant capacity.");
        }

        Instant now = Instant.now();
        if (session.getStatus() == WarSessionStatus.SCHEDULED
                && warSessionRepository.openWaitingRoom(warSessionId, now) != 1) {
            throw conflict("War Session status changed while opening the waiting room.");
        }
        warBatchRepository.openPendingBatches(warSessionId);

        if (warParticipantRepository.assignToWaitingRoom(
                participant.getId(), warSessionId, selectedBatch.getId(), now, nextQueuePosition, now) != 1) {
            throw conflict("Participant was modified concurrently. Please retry the request.");
        }

        participant.setBatchId(selectedBatch.getId());
        participant.setQueueEnteredAt(now);
        participant.setQueuePosition(nextQueuePosition);
        participant.setStatus(WarParticipantStatus.WAITING);
        participant.setUpdatedAt(now);

        return assignedResponse(session, participant, Instant.now(), selectedBatch);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "participantWaitingRooms",
            key = "T(com.ticket_war.war.util.ParticipantAccessTokenUtil).hash(#p0)",
            condition = "#p0 != null && !#p0.isBlank()")
    public WaitingRoomResponse getWaitingRoom(String accessToken) {
        WarParticipant participant = warParticipantService.getAuthenticatedParticipant(accessToken);
        UUID warSessionId = participant.getWarSessionId();
        WarSession session = warSessionRepository.findById(warSessionId)
                .orElseThrow(() -> sessionNotFound(warSessionId));
        Instant now = Instant.now();

        if (participant.getBatchId() == null && participant.getQueueEnteredAt() == null) {
            return new WaitingRoomResponse(
                    participant.getId(),
                    warSessionId,
                    null,
                    null,
                    session.getTotalBatches(),
                    null,
                    0,
                    null,
                    participant.getStatus(),
                    session.getWaitingRoomOpensAt(),
                    session.getChallengeStartsAt(),
                    isChallengeAvailable(session, now));
        }
        if (participant.getBatchId() == null || participant.getQueueEnteredAt() == null) {
            throw conflict("Participant waiting-room assignment is incomplete.");
        }
        return assignedResponse(session, participant, now);
    }

    private void validateSessionCanBeEntered(WarSession session) {
        if (session.getStatus() != WarSessionStatus.SCHEDULED
                && session.getStatus() != WarSessionStatus.WAITING_ROOM_OPEN) {
            throw conflict("The War Session is not accepting waiting-room entries.");
        }
        if (session.getWaitingRoomOpensAt() == null || session.getChallengeStartsAt() == null) {
            throw conflict("The War Session waiting-room schedule is not configured.");
        }
        Instant now = Instant.now();
        if (now.isBefore(session.getWaitingRoomOpensAt())) {
            throw conflict("The waiting room has not opened yet.");
        }
        if (!now.isBefore(session.getChallengeStartsAt())) {
            throw conflict("The challenge has already started.");
        }
    }

    private WaitingRoomResponse assignedResponse(
            WarSession session,
            WarParticipant participant,
            Instant now) {
        WarBatch batch = warBatchRepository.findByIdAndWarSessionId(
                        participant.getBatchId(), session.getId())
                .orElseThrow(() -> conflict("Participant is assigned to a batch outside the War Session."));
        return assignedResponse(session, participant, now, batch);
    }

    private WaitingRoomResponse assignedResponse(
            WarSession session,
            WarParticipant participant,
            Instant now,
            WarBatch batch) {
        BatchEntryStats stats = warParticipantRepository.getBatchEntryStats(batch.getId());
        return new WaitingRoomResponse(
                participant.getId(),
                session.getId(),
                batch.getId(),
                batch.getBatchNumber(),
                session.getTotalBatches(),
                participant.getQueuePosition(),
                stats.enteredCount(),
                batch.getParticipantCapacity(),
                participant.getStatus(),
                session.getWaitingRoomOpensAt(),
                session.getChallengeStartsAt(),
                isChallengeAvailable(session, now));
    }

    private static boolean isChallengeAvailable(WarSession session, Instant now) {
        return session.getChallengeStartsAt() != null
                && !now.isBefore(session.getChallengeStartsAt());
    }

    private static ResponseStatusException sessionNotFound(UUID warSessionId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "War Session not found: " + warSessionId);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
