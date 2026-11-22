package com.ticketwar.event_service.service;

import com.ticketwar.event_service.dto.booking.BookingStatusResponse;
import com.ticketwar.event_service.dto.booking.WarResultsFinalizedMessage;
import com.ticketwar.event_service.repository.BookingContextRepository;
import com.ticketwar.event_service.repository.BookingContextRepository.AlternateCandidate;
import com.ticketwar.event_service.repository.BookingContextRepository.FinalizedBatch;
import com.ticketwar.event_service.repository.BookingContextRepository.NotificationRecipient;
import com.ticketwar.event_service.repository.BookingContextRepository.ParticipantContext;
import com.ticketwar.event_service.repository.BookingContextRepository.WinnerCandidate;
import com.ticketwar.event_service.repository.BookingRepository;
import com.ticketwar.event_service.repository.BookingRepository.BatchKey;
import com.ticketwar.event_service.repository.BookingRepository.BookingRecord;
import com.ticketwar.event_service.repository.EventRepository;
import com.ticketwar.event_service.util.ParticipantAccessTokenUtil;
import com.ticketwar.event_service.util.ParticipantAccessTokenUtil.GeneratedToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingContextRepository bookingContextRepository;
    private final EventRepository eventRepository;
    private final ParticipantEmailService participantEmailService;
    private final Duration claimWindow;

    public BookingService(
            BookingRepository bookingRepository,
            BookingContextRepository bookingContextRepository,
            EventRepository eventRepository,
            ParticipantEmailService participantEmailService,
            @Value("${booking.claim-window:PT20M}") Duration claimWindow) {
        this.bookingRepository = bookingRepository;
        this.bookingContextRepository = bookingContextRepository;
        this.eventRepository = eventRepository;
        this.participantEmailService = participantEmailService;
        this.claimWindow = claimWindow;
    }

    @Transactional(readOnly = true)
    public BookingStatusResponse getMyStatus(String accessToken) {
        ParticipantContext participant = findParticipant(accessToken);
        requireFinalized(participant);

        BookingRecord booking = bookingRepository.findLatestBooking(participant.participantId()).orElse(null);
        if (booking != null) {
            if ("CLAIMED".equals(booking.status())) {
                return response("CLAIMED", participant, booking, "Your ticket claim is confirmed.");
            }
            if ("INVITED".equals(booking.status())) {
                if (booking.expiresAt().isAfter(Instant.now())) {
                    return response("INVITED", participant, booking,
                            "Claim your ticket before the invitation expires.");
                }
                return response("EXPIRED", participant, booking, "This ticket invitation has expired.");
            }
            if ("EXPIRED".equals(booking.status())) {
                return response("EXPIRED", participant, booking, "This ticket invitation has expired.");
            }
        }

        if ("WINNER".equals(participant.status()) || "ALTERNATE".equals(participant.status())) {
            if ("ALTERNATE".equals(participant.status())
                    && participant.warBatchId() != null
                    && participant.ticketQuota() != null
                    && !bookingRepository.hasUnclaimedTicket(participant.warSessionId(),
                            participant.warBatchId(), participant.ticketQuota())) {
                return new BookingStatusResponse("NOT_ELIGIBLE", participant.warSessionId(),
                        participant.batchNumber(), null, null,
                        "There are no unclaimed tickets remaining in your batch.");
            }
            return new BookingStatusResponse("WAITING", participant.warSessionId(),
                    participant.batchNumber(), null, null,
                    "There is no ticket invitation for you yet. Check again soon.");
        }
        return new BookingStatusResponse("NOT_ELIGIBLE", participant.warSessionId(),
                participant.batchNumber(), null, null,
                "Ticket claims are available to winners and promoted alternates.");
    }

    @Transactional
    public BookingStatusResponse claim(String accessToken) {
        ParticipantContext participant = findParticipant(accessToken);
        requireFinalized(participant);
        if (!"WINNER".equals(participant.status()) && !"ALTERNATE".equals(participant.status())) {
            throw conflict("Only winners and promoted alternates can claim a ticket.");
        }

        BookingRecord latest = bookingRepository.findLatestBooking(participant.participantId())
                .orElseThrow(() -> conflict("There is no active ticket invitation to claim."));
        if ("CLAIMED".equals(latest.status())) {
            return response("CLAIMED", participant, latest, "Your ticket claim is already confirmed.");
        }
        if (!"INVITED".equals(latest.status()) || !latest.expiresAt().isAfter(Instant.now())) {
            throw conflict("There is no active ticket invitation to claim.");
        }

        if (!bookingRepository.lockWarBatch(latest.warSessionId(), latest.warBatchId())) {
            throw conflict("The ticket invitation is no longer available.");
        }
        BookingRecord active = bookingRepository.lockBooking(latest.id())
                .orElseThrow(() -> conflict("The ticket invitation is no longer available."));
        Instant now = Instant.now();
        if (!bookingRepository.claimBooking(active.id(), now)) {
            throw conflict("The ticket invitation changed before it could be claimed. Refresh and try again.");
        }
        notifyClaimed(active);
        return response("CLAIMED", participant, active, "Your ticket claim is confirmed.");
    }

    @Transactional
    public BookingStatusResponse claimWithEmailLink(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw conflict("The ticket claim link is invalid.");
        }
        String tokenHash = ParticipantAccessTokenUtil.hash(rawToken);
        BookingRecord found = bookingRepository.findBookingByEmailTokenHash(tokenHash)
                .orElseThrow(() -> conflict("The ticket claim link is invalid or has expired."));
        if (!bookingRepository.lockWarBatch(found.warSessionId(), found.warBatchId())) {
            throw conflict("The ticket invitation is no longer available.");
        }

        BookingRecord active = bookingRepository.lockBooking(found.id())
                .orElseThrow(() -> conflict("The ticket invitation is no longer available."));
        if ("CLAIMED".equals(active.status())) {
            return response("CLAIMED", active, "Your ticket claim is confirmed.");
        }
        if (!"INVITED".equals(active.status())) {
            return response("EXPIRED", active, "This ticket invitation has expired.");
        }

        Instant now = Instant.now();
        if (!active.expiresAt().isAfter(now)) {
            bookingRepository.expireBooking(active.id(), now);
            return response("EXPIRED", active, "This ticket invitation has expired.");
        }
        if (!bookingRepository.claimBooking(active.id(), now)) {
            throw conflict("The ticket invitation changed before it could be claimed. Please open the link again.");
        }
        notifyClaimed(active);
        return response("CLAIMED", active, "Your ticket claim is confirmed.");
    }

    @Transactional
    public void handleResultsFinalized(WarResultsFinalizedMessage event) {
        Objects.requireNonNull(event, "Finalized results message is required.");
        if (!"WAR_RESULTS_FINALIZED".equals(event.eventType()) || event.eventVersion() != 1) {
            throw new IllegalArgumentException("Unsupported War results event type or version.");
        }
        if (!eventRepository.existsById(event.eventId())) {
            throw new IllegalStateException("Finalized results reference an Event that does not exist.");
        }

        for (WarResultsFinalizedMessage.BatchResults batch : event.batches().stream()
                .sorted(Comparator.comparing(WarResultsFinalizedMessage.BatchResults::batchId))
                .toList()) {
            if (!bookingRepository.lockWarBatch(event.warSessionId(), batch.batchId())) {
                throw new IllegalStateException("Finalized results reference a batch that does not exist.");
            }
            for (WarResultsFinalizedMessage.ParticipantResult winner : batch.winners()) {
                if (bookingRepository.hasBooking(event.warSessionId(), winner.participantId())) {
                    continue;
                }

                Instant invitedAt = Instant.now();
                Instant expiresAt = invitedAt.plus(claimWindow);
                GeneratedToken claimToken = ParticipantAccessTokenUtil.generate();
                bookingRepository.createBooking(
                        event.warSessionId(), batch.batchId(), winner.participantId(),
                        winner.competitionRank(), claimToken.hash(), invitedAt, expiresAt);
                participantEmailService.bookingInvitation(
                        winner.email(), event.sessionName(), claimToken.rawToken(),
                        batch.batchNumber(), winner.competitionRank(), expiresAt);
            }
        }
    }

    @Transactional
    public void initializeFinalizedSessions() {
        List<FinalizedBatch> batches = bookingContextRepository.findFinalizedBatchesMissingWinnerBookings();
        for (FinalizedBatch batch : batches) {
            if (!bookingRepository.lockWarBatch(batch.warSessionId(), batch.warBatchId())) {
                continue;
            }
            Instant invitedAt = Instant.now();
            Instant expiresAt = invitedAt.plus(claimWindow);
            List<WinnerCandidate> winners = bookingContextRepository.findUnbookedWinners(
                    batch.warSessionId(), batch.warBatchId());
            for (WinnerCandidate winner : winners) {
                GeneratedToken claimToken = ParticipantAccessTokenUtil.generate();
                bookingRepository.createBooking(
                        batch.warSessionId(), batch.warBatchId(), winner.participantId(),
                        winner.competitionRank(), claimToken.hash(), invitedAt, expiresAt);
                participantEmailService.bookingInvitation(
                        winner.email(), winner.sessionName(), claimToken.rawToken(),
                        winner.batchNumber(), winner.competitionRank(), expiresAt);
            }
        }
    }

    @Transactional
    public void advanceExpiredInvitations() {
        Instant now = Instant.now();
        for (BatchKey key : bookingRepository.findBatchesNeedingAdvance(now)) {
            if (!bookingRepository.lockWarBatch(key.warSessionId(), key.warBatchId())) {
                continue;
            }

            for (UUID expiredBookingId : bookingRepository.findExpiredBookingIds(
                    key.warSessionId(), key.warBatchId(), now)) {
                bookingRepository.expireBooking(expiredBookingId, now);
            }

            if (bookingRepository.hasActiveAlternate(key.warSessionId(), key.warBatchId(), now)) {
                continue;
            }

            Integer ticketNumber = bookingRepository.findOpenExpiredTicket(
                    key.warSessionId(), key.warBatchId()).orElse(null);
            if (ticketNumber == null) {
                continue;
            }
            AlternateCandidate alternate = bookingContextRepository.findNextAlternate(
                    key.warSessionId(), key.warBatchId()).orElse(null);
            if (alternate == null) {
                continue;
            }

            Instant invitedAt = Instant.now();
            Instant expiresAt = invitedAt.plus(claimWindow);
            GeneratedToken claimToken = ParticipantAccessTokenUtil.generate();
            bookingRepository.createBooking(
                    key.warSessionId(), key.warBatchId(), alternate.participantId(),
                    ticketNumber, claimToken.hash(), invitedAt, expiresAt);
            participantEmailService.bookingInvitation(
                    alternate.email(), alternate.sessionName(), claimToken.rawToken(),
                    alternate.batchNumber(), ticketNumber, expiresAt);
        }
    }

    private void notifyClaimed(BookingRecord booking) {
        bookingContextRepository.findNotificationRecipient(booking.participantId())
                .ifPresent((NotificationRecipient recipient) -> participantEmailService.ticketClaimed(
                        recipient.email(), recipient.sessionName(),
                        booking.batchNumber(), booking.ticketNumber()));
    }

    private ParticipantContext findParticipant(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Participant access token is required.");
        }
        return bookingContextRepository.findParticipantByTokenHash(ParticipantAccessTokenUtil.hash(accessToken))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Participant access token is invalid."));
    }

    private static void requireFinalized(ParticipantContext participant) {
        if (!participant.finalized()) {
            throw conflict("Ticket booking is available after the competition results are finalized.");
        }
    }

    private static BookingStatusResponse response(
            String status,
            ParticipantContext participant,
            BookingRecord booking,
            String message) {
        return new BookingStatusResponse(status, participant.warSessionId(),
                participant.batchNumber(), booking.ticketNumber(), booking.expiresAt(), message);
    }

    private static BookingStatusResponse response(String status, BookingRecord booking, String message) {
        return new BookingStatusResponse(status, booking.warSessionId(), booking.batchNumber(),
                booking.ticketNumber(), booking.expiresAt(), message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
