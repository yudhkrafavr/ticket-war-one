
package com.ticketwar.event_service.service;

import com.ticketwar.event_service.entity.Event;
import com.ticketwar.event_service.enums.EventStatus;
import com.ticketwar.event_service.repository.EventRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EventService {

    private final EventRepository eventRepository;

    // Create a new event
    public Event createEvent(Event event) {

        if (event.getTotalTickets() == null || event.getTotalTickets() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total tickets must be greater than zero"
            );
        }

        event.setStatus(EventStatus.DRAFT);

        return eventRepository.save(event);
    }

    // Get all events
    @Transactional(readOnly = true)
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    // Get event by ID
    @Transactional(readOnly = true)
    public Event getEventById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                ));
    }

    // Get events by status
    @Transactional(readOnly = true)
    public List<Event> getEventsByStatus(EventStatus status) {
        return eventRepository.findByStatus(status);
    }

    // Update an existing event
    public Event updateEvent(UUID id, Event updatedEvent) {

        Event existingEvent = getEventById(id);

        if (existingEvent.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only draft events can be updated"
            );
        }

        if (updatedEvent.getTotalTickets() == null ||
                updatedEvent.getTotalTickets() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total tickets must be greater than zero"
            );
        }

        existingEvent.setName(updatedEvent.getName());
        existingEvent.setDescription(updatedEvent.getDescription());
        existingEvent.setVenue(updatedEvent.getVenue());
        existingEvent.setEventDate(updatedEvent.getEventDate());
        existingEvent.setTotalTickets(updatedEvent.getTotalTickets());
        existingEvent.setRegistrationStartAt(updatedEvent.getRegistrationStartAt());
        existingEvent.setRegistrationEndAt(updatedEvent.getRegistrationEndAt());

        return eventRepository.save(existingEvent);
    }

    // Publish an event
    public Event publishEvent(UUID id) {

        Event event = getEventById(id);

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

        return eventRepository.save(event);
    }

    // Delete an event
    public void deleteEvent(UUID id) {

        Event event = getEventById(id);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only draft events can be deleted"
            );
        }

        eventRepository.delete(event);
    }
}
