package com.ticketwar.event_service.controller;

import com.ticketwar.event_service.dto.CreateEventRequest;
import com.ticketwar.event_service.dto.EventResponse;
import com.ticketwar.event_service.dto.UpdateEventRequest;
import com.ticketwar.event_service.enums.EventStatus;
import com.ticketwar.event_service.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // Create event
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse createdEvent = eventService.createEvent(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdEvent);
    }

    // Get all events, optionally filter by status
    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents(
            @RequestParam(required = false) EventStatus status) {

        List<EventResponse> events = (status == null)
                ? eventService.getAllEvents()
                : eventService.getEventsByStatus(status);

        return ResponseEntity.ok(events);
    }

    // Get event by ID
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable UUID id) {
        EventResponse event = eventService.getEventById(id);

        return ResponseEntity.ok(event);
    }

    // Update event
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request) {

        EventResponse updatedEvent = eventService.updateEvent(id, request);

        return ResponseEntity.ok(updatedEvent);
    }

    // Publish event
    @PatchMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publishEvent(@PathVariable UUID id) {
        EventResponse publishedEvent = eventService.publishEvent(id);

        return ResponseEntity.ok(publishedEvent);
    }

    // Delete event
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);

        return ResponseEntity.noContent().build();
    }
}
