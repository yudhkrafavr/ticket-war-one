package com.ticket_war.war.service;

import com.ticket_war.war.dto.response.ParticipantResultResponse;
import com.ticket_war.war.entity.ChallengeAttempt;
import com.ticket_war.war.entity.WarBatch;
import com.ticket_war.war.entity.WarParticipant;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.repository.ChallengeRepository;
import com.ticket_war.war.repository.WarBatchRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ParticipantResultService {

    private final WarParticipantService warParticipantService;
    private final WarSessionRepository warSessionRepository;
    private final WarBatchRepository warBatchRepository;
    private final ChallengeRepository challengeRepository;

    public ParticipantResultService(
            WarParticipantService warParticipantService,
            WarSessionRepository warSessionRepository,
            WarBatchRepository warBatchRepository,
            ChallengeRepository challengeRepository) {
        this.warParticipantService = warParticipantService;
        this.warSessionRepository = warSessionRepository;
        this.warBatchRepository = warBatchRepository;
        this.challengeRepository = challengeRepository;
    }

    @Transactional(readOnly = true)
    public ParticipantResultResponse getResult(String accessToken) {
        WarParticipant participant = warParticipantService.getAuthenticatedParticipant(accessToken);
        WarSession session = warSessionRepository.findById(participant.getWarSessionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "War Session not found: " + participant.getWarSessionId()));
        if (!Boolean.TRUE.equals(session.getLeaderboardFinalized())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Competition results are not available until the leaderboard is finalized.");
        }

        Integer batchNumber = null;
        if (participant.getBatchId() != null) {
            WarBatch batch = warBatchRepository.findByIdAndWarSessionId(
                            participant.getBatchId(), participant.getWarSessionId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Participant batch not found."));
            batchNumber = batch.getBatchNumber();
        }

        ChallengeAttempt attempt = challengeRepository.findAttemptByParticipantId(participant.getId())
                .orElse(null);
        return new ParticipantResultResponse(
                participant.getId(),
                session.getId(),
                session.getName(),
                session.getLeaderboardFinalizedAt(),
                batchNumber,
                participant.getQueuePosition(),
                participant.getCompetitionRank(),
                attempt == null ? null : attempt.getCompletionDurationMs(),
                participant.getStatus());
    }
}
