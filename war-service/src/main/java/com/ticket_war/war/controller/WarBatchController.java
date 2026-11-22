package com.ticket_war.war.controller;

import com.ticket_war.war.dto.response.WarBatchResponse;
import com.ticket_war.war.service.WarBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/war-sessions/{id}/batches")
@Tag(name = "War Batches", description = "View automatically configured War Session batches")
public class WarBatchController {

    private final WarBatchService warBatchService;

    public WarBatchController(WarBatchService warBatchService) {
        this.warBatchService = warBatchService;
    }

    // Get all batches for a War Session
    @GetMapping
    @Operation(summary = "Retrieve War Session batches")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "War Batches retrieved"),
            @ApiResponse(responseCode = "404", description = "War Session not found")
    })
    public ResponseEntity<List<WarBatchResponse>> getAll(
            @PathVariable("id") UUID warSessionId) {
        List<WarBatchResponse> batches = warBatchService.findAll(warSessionId);

        return ResponseEntity.ok(batches);
    }

    // Get War Batch by ID
    @GetMapping("/{batchId}")
    @Operation(summary = "Retrieve a War Batch by UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "War Batch retrieved"),
            @ApiResponse(responseCode = "404", description = "War Session or War Batch not found")
    })
    public ResponseEntity<WarBatchResponse> getById(
            @PathVariable("id") UUID warSessionId,
            @PathVariable UUID batchId) {
        WarBatchResponse batch = warBatchService.findById(warSessionId, batchId);

        return ResponseEntity.ok(batch);
    }

}
