package com.ticketwar.event_service.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingLifecycleScheduler {

    private final BookingService bookingService;

    public BookingLifecycleScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${booking.scheduler-delay-ms:5000}")
    public void advanceBookings() {
        bookingService.initializeFinalizedSessions();
        bookingService.advanceExpiredInvitations();
    }
}
