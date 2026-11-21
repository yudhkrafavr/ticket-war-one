
package com.ticketwar.event_service.service;

import com.ticketwar.event_service.dto.CreateEventRequest;
import com.ticketwar.event_service.dto.EventResponse;
import com.ticketwar.event_service.dto.UpdateEventRequest;
import com.ticketwar.event_service.entity.Event;
import com.ticketwar.event_service.enums.EventStatus;
import com.ticketwar.event_service.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // Create a new event
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = toEntity(request);
        validateTotalTickets(event.getTotalTickets());
        validateRegistrationPeriod(event.getRegistrationStartAt(), event.getRegistrationEndAt());

        event.setStatus(EventStatus.DRAFT);

        return toResponse(eventRepository.save(event));
    }

    // Get all events
    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        return toResponses(eventRepository.findAll());
    }

    // Get event by ID
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        return toResponse(findEventById(id));
    }

    // Get events by status
    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByStatus(EventStatus status) {
        return toResponses(eventRepository.findByStatus(status));
    }

    // Update an existing event
    public EventResponse updateEvent(UUID id, UpdateEventRequest request) {
        Event existingEvent = findEventById(id);

        if (existingEvent.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only draft events can be updated"
            );
        }

        Event updatedEvent = toEntity(request);
        validateTotalTickets(updatedEvent.getTotalTickets());
        validateRegistrationPeriod(updatedEvent.getRegistrationStartAt(), updatedEvent.getRegistrationEndAt());

        existingEvent.setName(updatedEvent.getName());
        existingEvent.setDescription(updatedEvent.getDescription());
        existingEvent.setVenue(updatedEvent.getVenue());
        existingEvent.setEventDate(updatedEvent.getEventDate());
        existingEvent.setTotalTickets(updatedEvent.getTotalTickets());
        existingEvent.setRegistrationStartAt(updatedEvent.getRegistrationStartAt());
        existingEvent.setRegistrationEndAt(updatedEvent.getRegistrationEndAt());

        return toResponse(eventRepository.save(existingEvent));
    }

    // Publish an event
    public EventResponse publishEvent(UUID id) {
        Event event = findEventById(id);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only draft events can be published"
            );
        }

        if (event.getName() == null || event.getName().isBlank()
                || event.getEventDate() == null
                || event.getRegistrationStartAt() == null
                || event.getRegistrationEndAt() == null
                || !event.getRegistrationEndAt().isAfter(event.getRegistrationStartAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event information or registration period is invalid"
            );
        }

        event.setStatus(EventStatus.PUBLISHED);

        return toResponse(eventRepository.save(event));
    }

    // Delete an event
    public void deleteEvent(UUID id) {
        Event event = findEventById(id);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only draft events can be deleted"
            );
        }

        eventRepository.delete(event);
    }

    private Event findEventById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                ));
    }

    private void validateTotalTickets(Integer totalTickets) {
        if (totalTickets == null || totalTickets <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total tickets must be greater than zero"
            );
        }
    }

    private void validateRegistrationPeriod(Instant registrationStartAt, Instant registrationEndAt) {
        if (registrationStartAt != null && registrationEndAt != null
                && !registrationEndAt.isAfter(registrationStartAt)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration end must be after registration start"
            );
        }
    }

    private Event toEntity(CreateEventRequest request) {
        Event event = new Event();
        event.setName(request.name());
        event.setDescription(request.description());
        event.setVenue(request.venue());
        event.setEventDate(request.eventDate());
        event.setTotalTickets(request.totalTickets());
        event.setRegistrationStartAt(request.registrationStartAt());
        event.setRegistrationEndAt(request.registrationEndAt());
        event.setStatus(EventStatus.DRAFT);
        return event;
    }

    private Event toEntity(UpdateEventRequest request) {
        Event event = new Event();
        event.setName(request.name());
        event.setDescription(request.description());
        event.setVenue(request.venue());
        event.setEventDate(request.eventDate());
        event.setTotalTickets(request.totalTickets());
        event.setRegistrationStartAt(request.registrationStartAt());
        event.setRegistrationEndAt(request.registrationEndAt());
        return event;
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getEventDate(),
                event.getTotalTickets(),
                event.getRegistrationStartAt(),
                event.getRegistrationEndAt(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private List<EventResponse> toResponses(List<Event> events) {
        return events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
