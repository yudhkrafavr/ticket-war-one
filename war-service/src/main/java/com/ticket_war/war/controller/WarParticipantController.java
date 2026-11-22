package com.ticket_war.war.controller;

import com.ticket_war.war.dto.request.RegisterParticipantRequest;
import com.ticket_war.war.dto.request.ValidateParticipantAccessRequest;
import com.ticket_war.war.dto.response.ParticipantAccessValidationResponse;
import com.ticket_war.war.dto.response.AdminParticipantResponse;
import com.ticket_war.war.dto.response.ParticipantProfileResponse;
import com.ticket_war.war.dto.response.ParticipantResultResponse;
import com.ticket_war.war.dto.response.RegisterParticipantResponse;
import com.ticket_war.war.service.ParticipantResultService;
import com.ticket_war.war.service.WarParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Participants", description = "Register and validate participant access")
public class WarParticipantController {

    private final WarParticipantService warParticipantService;
    private final ParticipantResultService participantResultService;

    public WarParticipantController(
            WarParticipantService warParticipantService,
            ParticipantResultService participantResultService) {
        this.warParticipantService = warParticipantService;
        this.participantResultService = participantResultService;
    }

    // Register participant for a War Session
    @PostMapping("/war-sessions/{warSessionId}/participants/register")
    @Operation(summary = "Register a participant for a War Session")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Participant registered"),
            @ApiResponse(responseCode = "400", description = "Invalid email or request"),
            @ApiResponse(responseCode = "404", description = "War Session not found"),
            @ApiResponse(responseCode = "409", description = "Registration is closed, capacity is full, or email is already registered")
    })
    public ResponseEntity<RegisterParticipantResponse> register(
            @PathVariable UUID warSessionId,
            @Valid @RequestBody RegisterParticipantRequest request) {
        RegisterParticipantResponse response = warParticipantService.register(warSessionId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Validate participant access token
    @PostMapping("/war/participants/validate-access")
    @Operation(summary = "Validate a participant access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access token is valid"),
            @ApiResponse(responseCode = "400", description = "Access token is required"),
            @ApiResponse(responseCode = "401", description = "Access token is invalid")
    })
    public ResponseEntity<ParticipantAccessValidationResponse> validateAccess(
            @Valid @RequestBody ValidateParticipantAccessRequest request) {
        ParticipantAccessValidationResponse response =
                warParticipantService.validateAccess(request.getAccessToken());

        return ResponseEntity.ok(response);
    }

    // Get authenticated participant profile
    @GetMapping("/war/participants/me")
    @Operation(summary = "Retrieve the authenticated participant profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participant profile retrieved"),
            @ApiResponse(responseCode = "401", description = "Access token is missing or invalid")
    })
    public ResponseEntity<ParticipantProfileResponse> getProfile(
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        ParticipantProfileResponse response = warParticipantService.getProfile(accessToken);

        return ResponseEntity.ok(response);
    }

    // Get authenticated participant's finalized competition result
    @GetMapping("/war/participants/me/result")
    @Operation(summary = "Retrieve the authenticated participant's finalized competition result")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participant result retrieved"),
            @ApiResponse(responseCode = "401", description = "Access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "War Session or batch not found"),
            @ApiResponse(responseCode = "409", description = "Leaderboard results are not yet available")
    })
    public ResponseEntity<ParticipantResultResponse> getResult(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        return ResponseEntity.ok(participantResultService.getResult(accessToken));
    }

    @GetMapping("/war-sessions/{warSessionId}/participants")
    @Operation(summary = "List participants registered for a War Session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participant roster retrieved"),
            @ApiResponse(responseCode = "401", description = "Admin sign-in is required"),
            @ApiResponse(responseCode = "404", description = "War Session not found")
    })
    public ResponseEntity<List<AdminParticipantResponse>> getAdminRoster(@PathVariable UUID warSessionId) {
        return ResponseEntity.ok(warParticipantService.getAdminRoster(warSessionId));
    }
}
