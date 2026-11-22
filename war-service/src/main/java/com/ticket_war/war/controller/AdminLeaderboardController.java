package com.ticket_war.war.controller;

import com.ticket_war.war.dto.response.AdminBatchLeaderboardDetailResponse;
import com.ticket_war.war.dto.response.AdminLeaderboardResponse;
import com.ticket_war.war.service.AdminLeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/war-sessions")
public class AdminLeaderboardController {

    private final AdminLeaderboardService adminLeaderboardService;

    public AdminLeaderboardController(AdminLeaderboardService adminLeaderboardService) {
        this.adminLeaderboardService = adminLeaderboardService;
    }

    @GetMapping("/{warSessionId}/leaderboard")
    public ResponseEntity<AdminLeaderboardResponse> getLeaderboard(
            @PathVariable UUID warSessionId) {
        AdminLeaderboardResponse leaderboard = adminLeaderboardService.getLeaderboard(warSessionId);
        return ResponseEntity.ok(leaderboard);
    }

    @GetMapping("/{warSessionId}/batches/{batchId}/leaderboard")
    public ResponseEntity<AdminBatchLeaderboardDetailResponse> getBatchLeaderboard(
            @PathVariable UUID warSessionId,
            @PathVariable UUID batchId) {
        AdminBatchLeaderboardDetailResponse leaderboard =
                adminLeaderboardService.getBatchLeaderboard(warSessionId, batchId);
        return ResponseEntity.ok(leaderboard);
    }
}
