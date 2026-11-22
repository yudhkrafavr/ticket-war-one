package com.ticket_war.war.service;

import com.ticket_war.war.dto.response.AdminBatchLeaderboardDetailResponse;
import com.ticket_war.war.dto.response.AdminBatchLeaderboardResponse;
import com.ticket_war.war.dto.response.AdminLeaderboardParticipantResponse;
import com.ticket_war.war.dto.response.AdminLeaderboardResponse;
import com.ticket_war.war.entity.WarSession;
import com.ticket_war.war.enums.WarParticipantStatus;
import com.ticket_war.war.repository.AdminLeaderboardRepository;
import com.ticket_war.war.repository.AdminLeaderboardRepository.LeaderboardRow;
import com.ticket_war.war.repository.WarBatchRepository;
import com.ticket_war.war.repository.WarSessionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminLeaderboardService {

    private final WarSessionRepository warSessionRepository;
    private final WarBatchRepository warBatchRepository;
    private final AdminLeaderboardRepository adminLeaderboardRepository;

    public AdminLeaderboardService(
            WarSessionRepository warSessionRepository,
            WarBatchRepository warBatchRepository,
            AdminLeaderboardRepository adminLeaderboardRepository) {
        this.warSessionRepository = warSessionRepository;
        this.warBatchRepository = warBatchRepository;
        this.adminLeaderboardRepository = adminLeaderboardRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "adminLeaderboards", key = "#p0")
    public AdminLeaderboardResponse getLeaderboard(UUID warSessionId) {
        WarSession session = getFinalizedSession(warSessionId);
        List<AdminBatchLeaderboardResponse> batches = toBatchResponses(
                adminLeaderboardRepository.findByWarSessionId(warSessionId));
        return new AdminLeaderboardResponse(
                session.getId(),
                session.getName(),
                session.getStatus(),
                true,
                session.getLeaderboardFinalizedAt(),
                session.getTotalParticipants(),
                session.getTotalBatches(),
                session.getTotalTickets(),
                batches);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "adminBatchLeaderboards", key = "#p0 + ':' + #p1")
    public AdminBatchLeaderboardDetailResponse getBatchLeaderboard(UUID warSessionId, UUID batchId) {
        WarSession session = getFinalizedSession(warSessionId);
        if (warBatchRepository.findByIdAndWarSessionId(batchId, warSessionId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "War Batch not found: " + batchId);
        }

        List<AdminBatchLeaderboardResponse> batches = toBatchResponses(
                adminLeaderboardRepository.findByWarSessionIdAndBatchId(warSessionId, batchId));
        AdminBatchLeaderboardResponse batch = batches.getFirst();
        return new AdminBatchLeaderboardDetailResponse(
                session.getId(),
                session.getName(),
                session.getStatus(),
                true,
                session.getLeaderboardFinalizedAt(),
                session.getTotalParticipants(),
                session.getTotalBatches(),
                session.getTotalTickets(),
                batch);
    }

    private WarSession getFinalizedSession(UUID warSessionId) {
        WarSession session = warSessionRepository.findById(warSessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "War Session not found: " + warSessionId));
        if (!Boolean.TRUE.equals(session.getLeaderboardFinalized())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Leaderboard results are not yet available because the War Session has not been finalized.");
        }
        return session;
    }

    private List<AdminBatchLeaderboardResponse> toBatchResponses(List<LeaderboardRow> rows) {
        Map<UUID, BatchResult> batches = new LinkedHashMap<>();
        for (LeaderboardRow row : rows) {
            BatchResult batch = batches.computeIfAbsent(row.batchId(), ignored -> new BatchResult(row));
            if (row.participantId() == null) {
                continue;
            }

            batch.totalParticipants++;
            if (row.competitionResult() == WarParticipantStatus.WINNER) {
                batch.winnerCount++;
            } else if (row.competitionResult() == WarParticipantStatus.ALTERNATE) {
                batch.alternateCount++;
            } else if (row.competitionResult() == WarParticipantStatus.LOST) {
                batch.lostCount++;
            }

            batch.participants.add(new AdminLeaderboardParticipantResponse(
                    row.participantId(),
                    row.email(),
                    row.competitionRank(),
                    row.queuePosition(),
                    row.queueEnteredAt(),
                    row.completionDurationMs(),
                    row.competitionResult()));
        }

        return batches.values().stream().map(BatchResult::toResponse).toList();
    }

    private static final class BatchResult {
        private final UUID batchId;
        private final Integer batchNumber;
        private final com.ticket_war.war.enums.WarBatchStatus batchStatus;
        private final Integer ticketQuota;
        private final Integer participantCapacity;
        private final List<AdminLeaderboardParticipantResponse> participants = new ArrayList<>();
        private long totalParticipants;
        private long winnerCount;
        private long alternateCount;
        private long lostCount;

        private BatchResult(LeaderboardRow row) {
            batchId = row.batchId();
            batchNumber = row.batchNumber();
            batchStatus = row.batchStatus();
            ticketQuota = row.ticketQuota();
            participantCapacity = row.participantCapacity();
        }

        private AdminBatchLeaderboardResponse toResponse() {
            return new AdminBatchLeaderboardResponse(
                    batchId,
                    batchNumber,
                    batchStatus,
                    ticketQuota,
                    participantCapacity,
                    totalParticipants,
                    winnerCount + alternateCount,
                    winnerCount,
                    alternateCount,
                    lostCount,
                    List.copyOf(participants));
        }
    }
}
