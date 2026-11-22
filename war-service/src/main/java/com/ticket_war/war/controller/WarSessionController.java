package com.ticket_war.war.controller;

import com.ticket_war.war.dto.request.CreateWarSessionRequest;
import com.ticket_war.war.dto.request.UpdateWarSessionRequest;
import com.ticket_war.war.dto.response.WarSessionResponse;
import com.ticket_war.war.enums.WarSessionStatus;
import com.ticket_war.war.service.WarSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/war-sessions")
public class WarSessionController {

    private final WarSessionService warSessionService;

    public WarSessionController(WarSessionService warSessionService) {
        this.warSessionService = warSessionService;
    }

    // Create War Session
    @PostMapping
    public ResponseEntity<WarSessionResponse> createWarSession(
            @Valid @RequestBody CreateWarSessionRequest request) {
        WarSessionResponse createdSession = warSessionService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdSession);
    }

    // Get all War Sessions, optionally filter by status
    @GetMapping
    public ResponseEntity<List<WarSessionResponse>> getAllWarSessions(
            @RequestParam(required = false) WarSessionStatus status) {
        List<WarSessionResponse> sessions = warSessionService.findAll(status);

        return ResponseEntity.ok(sessions);
    }

    // Get War Session by ID
    @GetMapping("/{id}")
    public ResponseEntity<WarSessionResponse> getWarSessionById(@PathVariable UUID id) {
        WarSessionResponse session = warSessionService.findById(id);

        return ResponseEntity.ok(session);
    }

    // Update War Session
    @PutMapping("/{id}")
    public ResponseEntity<WarSessionResponse> updateWarSession(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarSessionRequest request) {
        WarSessionResponse updatedSession = warSessionService.update(id, request);

        return ResponseEntity.ok(updatedSession);
    }

    // Publish War Session
    @PostMapping("/{id}/publish")
    public ResponseEntity<WarSessionResponse> publishWarSession(@PathVariable UUID id) {
        WarSessionResponse publishedSession = warSessionService.publish(id);

        return ResponseEntity.ok(publishedSession);
    }
}
