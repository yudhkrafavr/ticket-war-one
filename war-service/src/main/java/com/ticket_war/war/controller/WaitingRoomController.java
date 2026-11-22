package com.ticket_war.war.controller;

import com.ticket_war.war.dto.response.WaitingRoomResponse;
import com.ticket_war.war.service.WaitingRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/war/participants/me")
@Tag(name = "Participant Waiting Room", description = "Enter and view the participant waiting room")
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    public WaitingRoomController(WaitingRoomService waitingRoomService) {
        this.waitingRoomService = waitingRoomService;
    }

    // Enter participant waiting room
    @PostMapping("/enter-waiting-room")
    @Operation(summary = "Enter the waiting room and assign a batch")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participant entered or already has a waiting-room assignment"),
            @ApiResponse(responseCode = "401", description = "Participant access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "War Session not found"),
            @ApiResponse(responseCode = "409", description = "Waiting room is unavailable, participant state is invalid, or all batches are full")
    })
    public ResponseEntity<WaitingRoomResponse> enter(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        return ResponseEntity.ok(waitingRoomService.enter(accessToken));
    }

    // Get participant waiting-room status
    @GetMapping("/waiting-room")
    @Operation(summary = "Retrieve participant waiting-room status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Waiting-room status retrieved"),
            @ApiResponse(responseCode = "401", description = "Participant access token is missing or invalid"),
            @ApiResponse(responseCode = "404", description = "War Session not found")
    })
    public ResponseEntity<WaitingRoomResponse> getWaitingRoom(
            @Parameter(name = "X-Participant-Access-Token", in = ParameterIn.HEADER, required = true)
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        return ResponseEntity.ok(waitingRoomService.getWaitingRoom(accessToken));
    }

}
