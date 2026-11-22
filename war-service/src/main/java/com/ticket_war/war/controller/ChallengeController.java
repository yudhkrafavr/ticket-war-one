package com.ticket_war.war.controller;

import com.ticket_war.war.dto.request.SubmitChallengeRequest;
import com.ticket_war.war.dto.response.ChallengeResponse;
import com.ticket_war.war.dto.response.ChallengeStartResponse;
import com.ticket_war.war.dto.response.ChallengeSubmissionResponse;
import com.ticket_war.war.dto.response.PrepareChallengeResponse;
import com.ticket_war.war.service.ChallengeService;
import com.ticket_war.war.service.ChallengeService.PrepareResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/war/challenges")
@Tag(name = "Challenges", description = "Start, retrieve, and submit participant challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    // Prepare participant challenge questions
    @PostMapping("/prepare")
    @Operation(summary = "Prepare and persist a participant's randomized challenge questions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing challenge preparation returned"),
            @ApiResponse(responseCode = "201", description = "Challenge prepared"),
            @ApiResponse(responseCode = "401", description = "Participant access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "War Session or batch not found"),
            @ApiResponse(responseCode = "409", description = "Participant or challenge is not eligible for preparation")
    })
    public ResponseEntity<PrepareChallengeResponse> prepare(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        PrepareResult result = challengeService.prepare(accessToken);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    // Activate prepared participant challenge
    @PostMapping("/start")
    @Operation(summary = "Activate the prepared challenge at the shared scheduled start time")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge attempt activated or returned"),
            @ApiResponse(responseCode = "401", description = "Participant access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Prepared challenge attempt not found"),
            @ApiResponse(responseCode = "409", description = "Participant or challenge is not eligible to start")
    })
    public ResponseEntity<ChallengeStartResponse> start(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        return ResponseEntity.ok(challengeService.start(accessToken));
    }

    // Get assigned challenge questions
    @GetMapping("/{attemptId}")
    @Operation(summary = "Retrieve the participant's assigned challenge questions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge retrieved"),
            @ApiResponse(responseCode = "401", description = "Participant access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Challenge attempt not found for this participant"),
            @ApiResponse(responseCode = "409", description = "Challenge questions are not yet available")
    })
    public ResponseEntity<ChallengeResponse> getChallenge(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken,
            @PathVariable UUID attemptId) {
        return ResponseEntity.ok(challengeService.getChallenge(accessToken, attemptId));
    }

    // Submit challenge answers
    @PostMapping("/{attemptId}/submit")
    @Operation(summary = "Submit challenge answers or submit automatically after the deadline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge submitted"),
            @ApiResponse(responseCode = "400", description = "Submitted answer is invalid"),
            @ApiResponse(responseCode = "401", description = "Participant access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Challenge attempt not found for this participant"),
            @ApiResponse(responseCode = "409", description = "Challenge attempt is no longer in progress")
    })
    public ResponseEntity<ChallengeSubmissionResponse> submit(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken,
            @PathVariable UUID attemptId,
            @Valid @RequestBody(required = false) SubmitChallengeRequest request) {
        return ResponseEntity.ok(challengeService.submit(accessToken, attemptId, request));
    }
}
