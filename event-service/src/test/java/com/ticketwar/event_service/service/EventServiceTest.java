package com.ticketwar.event_service.service;

import com.ticketwar.event_service.dto.CreateEventRequest;
import com.ticketwar.event_service.dto.EventResponse;
import com.ticketwar.event_service.dto.UpdateEventRequest;
import com.ticketwar.event_service.entity.Event;
import com.ticketwar.event_service.enums.EventStatus;
import com.ticketwar.event_service.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("c34b7b2a-8fb2-4f35-90d9-35a1079c65e1");
    private static final Instant CREATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-11T09:00:00Z");
    private static final Instant REGISTRATION_START = Instant.parse("2099-06-01T09:00:00Z");
    private static final Instant REGISTRATION_END = Instant.parse("2099-06-10T09:00:00Z");
    private static final Instant EVENT_DATE = Instant.parse("2099-06-15T12:00:00Z");
    private static final Instant PAST_EVENT_DATE = Instant.parse("2000-06-15T12:00:00Z");

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createEventWithValidDataSavesEventAsDraft() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event savedEvent = invocation.getArgument(0);
            savedEvent.setId(EVENT_ID);
            savedEvent.setCreatedAt(CREATED_AT);
            savedEvent.setUpdatedAt(UPDATED_AT);
            return savedEvent;
        });

        EventResponse response = eventService.createEvent(validCreateRequest());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals(EventStatus.DRAFT, eventCaptor.getValue().getStatus());
        assertEquals(EVENT_ID, response.id());
        assertEquals(EventStatus.DRAFT, response.status());
        assertEquals("Summer Concert", response.name());
        assertEquals(500, response.totalTickets().intValue());
    }

    @Test
    void createDraftEventAllowsMissingRegistrationDates() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.createEvent(createRequest(500, null, null, EVENT_DATE));

        assertEquals(EventStatus.DRAFT, response.status());
        assertNull(response.registrationStartAt());
        assertNull(response.registrationEndAt());
    }

    @Test
    void createEventRejectsBlankName() {
        CreateEventRequest request = new CreateEventRequest(
                "   ",
                "Live music event",
                "City Arena",
                EVENT_DATE,
                500,
                REGISTRATION_START,
                REGISTRATION_END
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(request)
        );

        assertBadRequest(exception);
        assertEquals("Event name must not be empty", exception.getReason());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEventRejectsZeroTickets() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(createRequest(0, REGISTRATION_START, REGISTRATION_END, EVENT_DATE))
        );

        assertBadRequest(exception);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEventRejectsNegativeTickets() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(createRequest(-5, REGISTRATION_START, REGISTRATION_END, EVENT_DATE))
        );

        assertBadRequest(exception);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEventRejectsRegistrationEndBeforeStart() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(createRequest(
                        500,
                        REGISTRATION_START,
                        REGISTRATION_START.minusSeconds(60),
                        EVENT_DATE
                ))
        );

        assertBadRequest(exception);
        assertEquals("Registration end must be after registration start", exception.getReason());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEventRejectsRegistrationEndingAtOrAfterEventDate() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.createEvent(createRequest(
                        500,
                        REGISTRATION_START,
                        EVENT_DATE,
                        EVENT_DATE
                ))
        );

        assertBadRequest(exception);
        assertEquals("Registration must end before the event date", exception.getReason());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void getAllEventsReturnsMappedResponses() {
        Event draftEvent = eventWithStatus(EventStatus.DRAFT);
        Event publishedEvent = eventWithStatus(EventStatus.PUBLISHED);
        when(eventRepository.findAll()).thenReturn(List.of(draftEvent, publishedEvent));

        List<EventResponse> responses = eventService.getAllEvents();

        assertEquals(2, responses.size());
        assertEquals(EventStatus.DRAFT, responses.get(0).status());
        assertEquals(EventStatus.PUBLISHED, responses.get(1).status());
        assertEquals(EVENT_ID, responses.get(0).id());
        verify(eventRepository).findAll();
    }

    @Test
    void getEventByIdReturnsMappedExistingEvent() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(eventWithStatus(EventStatus.DRAFT)));

        EventResponse response = eventService.getEventById(EVENT_ID);

        assertEquals(EVENT_ID, response.id());
        assertEquals("Summer Concert", response.name());
        assertEquals(EventStatus.DRAFT, response.status());
        assertEquals(CREATED_AT, response.createdAt());
        assertEquals(UPDATED_AT, response.updatedAt());
    }

    @Test
    void getEventByIdReturnsNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.getEventById(EVENT_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getEventsByStatusReturnsOnlyRequestedStatus() {
        Event publishedEvent = eventWithStatus(EventStatus.PUBLISHED);
        when(eventRepository.findByStatus(EventStatus.PUBLISHED)).thenReturn(List.of(publishedEvent));

        List<EventResponse> responses = eventService.getEventsByStatus(EventStatus.PUBLISHED);

        assertEquals(1, responses.size());
        assertEquals(EventStatus.PUBLISHED, responses.getFirst().status());
        verify(eventRepository).findByStatus(EventStatus.PUBLISHED);
    }

    @Test
    void updateDraftEventChangesEditableFieldsAndPreservesIdAndStatus() {
        Event existingEvent = eventWithStatus(EventStatus.DRAFT);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateEventRequest request = new UpdateEventRequest(
                "Updated Concert",
                "Updated description",
                "New Arena",
                EVENT_DATE.plusSeconds(3600),
                750,
                REGISTRATION_START,
                REGISTRATION_END
        );

        EventResponse response = eventService.updateEvent(EVENT_ID, request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals("Updated Concert", response.name());
        assertEquals(750, response.totalTickets().intValue());
        assertEquals(EVENT_ID, eventCaptor.getValue().getId());
        assertEquals(EventStatus.DRAFT, eventCaptor.getValue().getStatus());
        assertEquals(CREATED_AT, eventCaptor.getValue().getCreatedAt());
        assertEquals(UPDATED_AT, eventCaptor.getValue().getUpdatedAt());
    }

    @Test
    void updateEventRejectsPublishedEvent() {
        assertUpdateRejectedForStatus(EventStatus.PUBLISHED);
    }

    @Test
    void updateEventRejectsCancelledEvent() {
        assertUpdateRejectedForStatus(EventStatus.CANCELLED);
    }

    @Test
    void updateEventRejectsZeroTickets() {
        assertUpdateRejectedForRequest(updateRequest(0, REGISTRATION_START, REGISTRATION_END, EVENT_DATE));
    }

    @Test
    void updateEventRejectsNegativeTickets() {
        assertUpdateRejectedForRequest(updateRequest(-2, REGISTRATION_START, REGISTRATION_END, EVENT_DATE));
    }

    @Test
    void updateEventRejectsBlankName() {
        Event existingEvent = eventWithStatus(EventStatus.DRAFT);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));
        UpdateEventRequest request = new UpdateEventRequest(
                "",
                "Updated description",
                "New Arena",
                EVENT_DATE,
                500,
                REGISTRATION_START,
                REGISTRATION_END
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(EVENT_ID, request)
        );

        assertBadRequest(exception);
        assertEquals("Event name must not be empty", exception.getReason());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void updateEventRejectsRegistrationEndBeforeStart() {
        assertUpdateRejectedForRequest(updateRequest(
                500,
                REGISTRATION_START,
                REGISTRATION_START.minusSeconds(60),
                EVENT_DATE
        ));
    }

    @Test
    void updateEventRejectsRegistrationEndAtOrAfterEventDate() {
        assertUpdateRejectedForRequest(updateRequest(500, REGISTRATION_START, EVENT_DATE, EVENT_DATE));
    }

    @Test
    void publishDraftEventWithValidDataChangesStatusToPublished() {
        Event draftEvent = eventWithStatus(EventStatus.DRAFT);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(draftEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.publishEvent(EVENT_ID);

        assertEquals(EventStatus.PUBLISHED, draftEvent.getStatus());
        assertEquals(EventStatus.PUBLISHED, response.status());
        verify(eventRepository).save(draftEvent);
    }

    @Test
    void publishEventRejectsPastEventDate() {
        Event event = validDraftEvent();
        event.setEventDate(PAST_EVENT_DATE);
        event.setRegistrationStartAt(PAST_EVENT_DATE.minusSeconds(7200));
        event.setRegistrationEndAt(PAST_EVENT_DATE.minusSeconds(3600));

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Event date must be in the future", exception.getReason());
    }

    @Test
    void publishEventRejectsMissingName() {
        Event event = validDraftEvent();
        event.setName("   ");

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Event name must not be empty", exception.getReason());
    }

    @Test
    void publishEventRejectsMissingEventDate() {
        Event event = validDraftEvent();
        event.setEventDate(null);

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Event date is required", exception.getReason());
    }

    @Test
    void publishEventRejectsMissingRegistrationStart() {
        Event event = validDraftEvent();
        event.setRegistrationStartAt(null);

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Registration start date is required", exception.getReason());
    }

    @Test
    void publishEventRejectsMissingRegistrationEnd() {
        Event event = validDraftEvent();
        event.setRegistrationEndAt(null);

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Registration end date is required", exception.getReason());
    }

    @Test
    void publishEventRejectsInvalidRegistrationPeriod() {
        Event event = validDraftEvent();
        event.setRegistrationEndAt(REGISTRATION_START);

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Registration end must be after registration start", exception.getReason());
    }

    @Test
    void publishEventRejectsRegistrationEndingAfterEventDate() {
        Event event = validDraftEvent();
        event.setRegistrationEndAt(EVENT_DATE.plusSeconds(1));

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Registration must end before the event date", exception.getReason());
    }

    @Test
    void publishEventRejectsNonPositiveTicketCount() {
        Event event = validDraftEvent();
        event.setTotalTickets(0);

        ResponseStatusException exception = assertPublishRejected(event);

        assertEquals("Total tickets must be greater than zero", exception.getReason());
    }

    @Test
    void publishEventRejectsAlreadyPublishedEvent() {
        Event event = eventWithStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.publishEvent(EVENT_ID)
        );

        assertBadRequest(exception);
        assertEquals("Only draft events can be published", exception.getReason());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void deleteDraftEventRemovesItFromRepository() {
        Event draftEvent = eventWithStatus(EventStatus.DRAFT);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(draftEvent));

        eventService.deleteEvent(EVENT_ID);

        verify(eventRepository).delete(draftEvent);
    }

    @Test
    void deleteEventRejectsPublishedEvent() {
        assertDeleteRejectedForStatus(EventStatus.PUBLISHED);
    }

    @Test
    void deleteEventRejectsCancelledEvent() {
        assertDeleteRejectedForStatus(EventStatus.CANCELLED);
    }

    @Test
    void deleteEventReturnsNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.deleteEvent(EVENT_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(eventRepository, never()).delete(any(Event.class));
    }

    private void assertUpdateRejectedForStatus(EventStatus status) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(eventWithStatus(status)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(EVENT_ID, validUpdateRequest())
        );

        assertBadRequest(exception);
        assertEquals("Only draft events can be updated", exception.getReason());
        verify(eventRepository, never()).save(any(Event.class));
    }

    private void assertUpdateRejectedForRequest(UpdateEventRequest request) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(eventWithStatus(EventStatus.DRAFT)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.updateEvent(EVENT_ID, request)
        );

        assertBadRequest(exception);
        verify(eventRepository, never()).save(any(Event.class));
    }

    private void assertDeleteRejectedForStatus(EventStatus status) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(eventWithStatus(status)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.deleteEvent(EVENT_ID)
        );

        assertBadRequest(exception);
        assertEquals("Only draft events can be deleted", exception.getReason());
        verify(eventRepository, never()).delete(any(Event.class));
    }

    private ResponseStatusException assertPublishRejected(Event event) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> eventService.publishEvent(EVENT_ID)
        );

        assertBadRequest(exception);
        verify(eventRepository, never()).save(any(Event.class));
        return exception;
    }

    private static void assertBadRequest(ResponseStatusException exception) {
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static CreateEventRequest validCreateRequest() {
        return createRequest(500, REGISTRATION_START, REGISTRATION_END, EVENT_DATE);
    }

    private static CreateEventRequest createRequest(
            Integer totalTickets,
            Instant registrationStartAt,
            Instant registrationEndAt,
            Instant eventDate) {
        return new CreateEventRequest(
                "Summer Concert",
                "Live music event",
                "City Arena",
                eventDate,
                totalTickets,
                registrationStartAt,
                registrationEndAt
        );
    }

    private static UpdateEventRequest validUpdateRequest() {
        return updateRequest(600, REGISTRATION_START, REGISTRATION_END, EVENT_DATE);
    }

    private static UpdateEventRequest updateRequest(
            Integer totalTickets,
            Instant registrationStartAt,
            Instant registrationEndAt,
            Instant eventDate) {
        return new UpdateEventRequest(
                "Updated Concert",
                "Updated description",
                "New Arena",
                eventDate,
                totalTickets,
                registrationStartAt,
                registrationEndAt
        );
    }

    private static Event validDraftEvent() {
        return eventWithStatus(EventStatus.DRAFT);
    }

    private static Event eventWithStatus(EventStatus status) {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setName("Summer Concert");
        event.setDescription("Live music event");
        event.setVenue("City Arena");
        event.setEventDate(EVENT_DATE);
        event.setTotalTickets(500);
        event.setRegistrationStartAt(REGISTRATION_START);
        event.setRegistrationEndAt(REGISTRATION_END);
        event.setStatus(status);
        event.setCreatedAt(CREATED_AT);
        event.setUpdatedAt(UPDATED_AT);
        return event;
    }
}
