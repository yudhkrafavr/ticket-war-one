package com.ticket_war.war.service;

import com.ticket_war.war.repository.LeaderboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeaderboardFinalizationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderboardFinalizationScheduler.class);

    private final LeaderboardRepository leaderboardRepository;
    private final LeaderboardFinalizationService leaderboardFinalizationService;

    public LeaderboardFinalizationScheduler(
            LeaderboardRepository leaderboardRepository,
            LeaderboardFinalizationService leaderboardFinalizationService) {
        this.leaderboardRepository = leaderboardRepository;
        this.leaderboardFinalizationService = leaderboardFinalizationService;
    }

    @Scheduled(fixedDelayString = "${war.leaderboard.finalization-check-interval-ms:5000}")
    public void finalizeExpiredSessions() {
        for (UUID warSessionId : leaderboardRepository.findDueSessionIds()) {
            try {
                leaderboardFinalizationService.finalizeIfDue(warSessionId);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not finalize leaderboard for War Session {}", warSessionId, exception);
            }
        }
    }
}
