package com.ticketwar.event_service.controller;

import com.ticketwar.event_service.entity.Event;
import com.ticketwar.event_service.enums.EventStatus;
import com.ticketwar.event_service.service.EventService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // Create event
    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        Event createdEvent = eventService.createEvent(event);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdEvent);
    }

    // Get all events, optionally filter by status
    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents(
            @RequestParam(required = false) EventStatus status) {

        List<Event> events = (status == null)
                ? eventService.getAllEvents()
                : eventService.getEventsByStatus(status);

        return ResponseEntity.ok(events);
    }

    // Get event by ID
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable UUID id) {
        Event event = eventService.getEventById(id);

        return ResponseEntity.ok(event);
    }

    // Update event
    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(
            @PathVariable UUID id,
            @RequestBody Event event) {

        Event updatedEvent = eventService.updateEvent(id, event);

        return ResponseEntity.ok(updatedEvent);
    }

    // Publish event
    @PatchMapping("/{id}/publish")
    public ResponseEntity<Event> publishEvent(@PathVariable UUID id) {
        Event publishedEvent = eventService.publishEvent(id);

        return ResponseEntity.ok(publishedEvent);
    }

    // Delete event
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);

        return ResponseEntity.noContent().build();
    }
}
