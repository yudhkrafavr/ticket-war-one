package com.ticketwar.event_service.controller;

import com.ticketwar.event_service.dto.booking.BookingStatusResponse;
import com.ticketwar.event_service.dto.booking.ClaimInvitationRequest;
import com.ticketwar.event_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings/me")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<BookingStatusResponse> getMyBooking(
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        return ResponseEntity.ok(bookingService.getMyStatus(accessToken));
    }

    @PostMapping("/claim")
    public ResponseEntity<BookingStatusResponse> claimTicket(
            @RequestHeader(value = "X-Participant-Access-Token", required = false) String accessToken) {
        return ResponseEntity.ok(bookingService.claim(accessToken));
    }

    @PostMapping("/claim-invitation")
    public ResponseEntity<BookingStatusResponse> claimFromEmailLink(
            @Valid @RequestBody ClaimInvitationRequest request) {
        return ResponseEntity.ok(bookingService.claimWithEmailLink(request.token()));
    }
}
