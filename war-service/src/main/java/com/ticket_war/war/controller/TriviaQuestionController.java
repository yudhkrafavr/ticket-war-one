package com.ticket_war.war.controller;

import com.ticket_war.war.dto.request.CreateTriviaQuestionRequest;
import com.ticket_war.war.dto.request.UpdateTriviaQuestionRequest;
import com.ticket_war.war.dto.response.TriviaQuestionResponse;
import com.ticket_war.war.service.TriviaQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/war-sessions/{id}/questions")
@Tag(name = "Trivia Questions", description = "Manage War Session trivia questions and options")
public class TriviaQuestionController {

    private final TriviaQuestionService triviaQuestionService;

    public TriviaQuestionController(TriviaQuestionService triviaQuestionService) {
        this.triviaQuestionService = triviaQuestionService;
    }

    // Create Trivia Question with options
    @PostMapping
    @Operation(summary = "Create a trivia question with answer options")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Trivia Question created"),
            @ApiResponse(responseCode = "400", description = "Invalid question or options"),
            @ApiResponse(responseCode = "404", description = "War Session not found"),
            @ApiResponse(responseCode = "409", description = "War Session cannot be modified or question order already exists")
    })
    public ResponseEntity<TriviaQuestionResponse> create(
            @PathVariable("id") UUID warSessionId,
            @Valid @RequestBody CreateTriviaQuestionRequest request) {
        TriviaQuestionResponse createdQuestion = triviaQuestionService.create(warSessionId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdQuestion);
    }

    // Get all Trivia Questions for a War Session
    @GetMapping
    @Operation(summary = "Retrieve all trivia questions for a War Session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trivia Questions retrieved"),
            @ApiResponse(responseCode = "404", description = "War Session not found")
    })
    public ResponseEntity<List<TriviaQuestionResponse>> getAll(
            @PathVariable("id") UUID warSessionId) {
        List<TriviaQuestionResponse> questions = triviaQuestionService.findAll(warSessionId);

        return ResponseEntity.ok(questions);
    }

    // Get Trivia Question by ID
    @GetMapping("/{questionId}")
    @Operation(summary = "Retrieve a trivia question with its options")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trivia Question retrieved"),
            @ApiResponse(responseCode = "404", description = "War Session or Trivia Question not found")
    })
    public ResponseEntity<TriviaQuestionResponse> getById(
            @PathVariable("id") UUID warSessionId,
            @PathVariable UUID questionId) {
        TriviaQuestionResponse question = triviaQuestionService.findById(warSessionId, questionId);

        return ResponseEntity.ok(question);
    }

    // Update Trivia Question and replace options
    @PutMapping("/{questionId}")
    @Operation(summary = "Update a trivia question and replace its options")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trivia Question updated"),
            @ApiResponse(responseCode = "400", description = "Invalid question or options"),
            @ApiResponse(responseCode = "404", description = "War Session or Trivia Question not found"),
            @ApiResponse(responseCode = "409", description = "War Session cannot be modified or question order already exists")
    })
    public ResponseEntity<TriviaQuestionResponse> update(
            @PathVariable("id") UUID warSessionId,
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateTriviaQuestionRequest request) {
        TriviaQuestionResponse updatedQuestion =
                triviaQuestionService.update(warSessionId, questionId, request);

        return ResponseEntity.ok(updatedQuestion);
    }

    // Delete Trivia Question and its options
    @DeleteMapping("/{questionId}")
    @Operation(summary = "Delete a trivia question and its options")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Trivia Question deleted"),
            @ApiResponse(responseCode = "404", description = "War Session or Trivia Question not found"),
            @ApiResponse(responseCode = "409", description = "War Session cannot be modified or concurrent modification occurred")
    })
    public ResponseEntity<Void> delete(
            @PathVariable("id") UUID warSessionId,
            @PathVariable UUID questionId) {
        triviaQuestionService.delete(warSessionId, questionId);

        return ResponseEntity.noContent().build();
    }
}
