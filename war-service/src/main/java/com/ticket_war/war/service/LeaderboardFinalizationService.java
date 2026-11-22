package com.ticket_war.war.service;

import com.ticket_war.war.dto.event.WarResultsFinalizedMessage;
import com.ticket_war.war.dto.event.WarResultsFinalizedMessage.BatchResults;
import com.ticket_war.war.dto.event.WarResultsFinalizedMessage.ParticipantResult;
import com.ticket_war.war.event.WarResultsFinalizedApplicationEvent;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.repository.LeaderboardRepository;
import com.ticket_war.war.repository.LeaderboardRepository.FinalizedResultRow;
import com.ticket_war.war.repository.WarSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LeaderboardFinalizationService {

    private final WarSessionRepository warSessionRepository;
    private final LeaderboardRepository leaderboardRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public LeaderboardFinalizationService(
            WarSessionRepository warSessionRepository,
            LeaderboardRepository leaderboardRepository,
            ApplicationEventPublisher applicationEventPublisher) {
        this.warSessionRepository = warSessionRepository;
        this.leaderboardRepository = leaderboardRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public boolean finalizeIfDue(UUID warSessionId) {
        WarSession session = warSessionRepository.findByIdForUpdate(warSessionId).orElse(null);
        if (session == null || Boolean.TRUE.equals(session.getLeaderboardFinalized())) {
            return false;
        }
        if (session.getStatus() == WarSessionStatus.DRAFT
                || session.getStatus() == WarSessionStatus.CANCELLED
                || session.getChallengeStartsAt() == null
                || session.getChallengeDurationSeconds() == null
                || session.getChallengeDurationSeconds() <= 0) {
            return false;
        }

        Instant deadline = session.getChallengeStartsAt()
                .plusSeconds(session.getChallengeDurationSeconds());
        Instant finalizedAt = Instant.now();
        if (finalizedAt.isBefore(deadline)) {
            return false;
        }

        leaderboardRepository.expireUnresolvedAttempts(warSessionId, finalizedAt);
        leaderboardRepository.markParticipantsLost(warSessionId, finalizedAt);
        leaderboardRepository.rankQualifiedParticipants(warSessionId, finalizedAt);
        leaderboardRepository.completeBatches(warSessionId, finalizedAt);

        if (warSessionRepository.finalizeLeaderboard(warSessionId, finalizedAt) != 1) {
            throw new IllegalStateException("War Session leaderboard finalization state changed unexpectedly.");
        }
        leaderboardRepository.flushChanges();
        WarResultsFinalizedMessage message = createFinalizedMessage(
                session.getEventId(), session.getName(), warSessionId, finalizedAt);
        applicationEventPublisher.publishEvent(new WarResultsFinalizedApplicationEvent(message));
        return true;
    }

    private WarResultsFinalizedMessage createFinalizedMessage(
            UUID eventId, String sessionName, UUID warSessionId, Instant finalizedAt) {
        Map<UUID, BatchResultsBuilder> batches = new LinkedHashMap<>();
        List<FinalizedResultRow> results = leaderboardRepository.findFinalizedResults(warSessionId);
        for (FinalizedResultRow result : results) {
            BatchResultsBuilder batch = batches.computeIfAbsent(
                    result.batchId(), ignored -> new BatchResultsBuilder(
                            result.batchNumber(), result.ticketQuota()));
            ParticipantResult participant = new ParticipantResult(
                    result.participantId(), result.email(), result.competitionRank());
            if ("WINNER".equals(result.status())) {
                batch.winners.add(participant);
            } else if ("ALTERNATE".equals(result.status())) {
                batch.alternates.add(participant);
            }
        }

        List<BatchResults> batchResults = batches.entrySet().stream()
                .map(entry -> new BatchResults(
                        entry.getKey(),
                        entry.getValue().batchNumber,
                        entry.getValue().ticketQuota,
                        List.copyOf(entry.getValue().winners),
                        List.copyOf(entry.getValue().alternates)))
                .toList();
        UUID eventMessageId = UUID.nameUUIDFromBytes(
                ("ticket-war:WAR_RESULTS_FINALIZED:v1:" + warSessionId).getBytes(StandardCharsets.UTF_8));
        return new WarResultsFinalizedMessage(
                eventId,
                sessionName,
                eventMessageId,
                "WAR_RESULTS_FINALIZED",
                1,
                warSessionId,
                finalizedAt,
                batchResults);
    }

    private static final class BatchResultsBuilder {
        private final int batchNumber;
        private final int ticketQuota;
        private final List<ParticipantResult> winners = new ArrayList<>();
        private final List<ParticipantResult> alternates = new ArrayList<>();

        private BatchResultsBuilder(int batchNumber, int ticketQuota) {
            this.batchNumber = batchNumber;
            this.ticketQuota = ticketQuota;
        }
    }
}
