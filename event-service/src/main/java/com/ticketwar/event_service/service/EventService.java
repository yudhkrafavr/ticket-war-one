
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

    public EventResponse createEvent(CreateEventRequest request) {
        Event event = toEntity(request);
        validateEventName(event.getName());
        validateTotalTickets(event.getTotalTickets());
        validateRegistrationSchedule(
                event.getRegistrationStartAt(),
                event.getRegistrationEndAt(),
                event.getEventDate()
        );

        event.setStatus(EventStatus.DRAFT);

        return toResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        return toResponses(eventRepository.findAll());
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        return toResponse(findEventById(id));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByStatus(EventStatus status) {
        return toResponses(eventRepository.findByStatus(status));
    }

    public EventResponse updateEvent(UUID id, UpdateEventRequest request) {
        Event existingEvent = findEventById(id);

        if (existingEvent.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only draft events can be updated"
            );
        }

        Event updatedEvent = toEntity(request);
        validateEventName(updatedEvent.getName());
        validateTotalTickets(updatedEvent.getTotalTickets());
        validateRegistrationSchedule(
                updatedEvent.getRegistrationStartAt(),
                updatedEvent.getRegistrationEndAt(),
                updatedEvent.getEventDate()
        );

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

        validateEventName(event.getName());
        validateTotalTickets(event.getTotalTickets());

        if (event.getEventDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event date is required"
            );
        }
        if (!event.getEventDate().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event date must be in the future"
            );
        }
        if (event.getRegistrationStartAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration start date is required"
            );
        }
        if (event.getRegistrationEndAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration end date is required"
            );
        }

        validateRegistrationSchedule(
                event.getRegistrationStartAt(),
                event.getRegistrationEndAt(),
                event.getEventDate()
        );

        event.setStatus(EventStatus.PUBLISHED);

        return toResponse(eventRepository.save(event));
    }

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

    private void validateEventName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event name must not be empty"
            );
        }
    }

    private void validateRegistrationSchedule(
            Instant registrationStartAt,
            Instant registrationEndAt,
            Instant eventDate) {
        if (registrationStartAt != null && registrationEndAt != null
                && !registrationEndAt.isAfter(registrationStartAt)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration end must be after registration start"
            );
        }
        if (registrationEndAt != null && eventDate != null
                && !registrationEndAt.isBefore(eventDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration must end before the event date"
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
