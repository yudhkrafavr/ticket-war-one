package com.ticketwar.event_service.service;

import com.ticketwar.event_service.service.ParticipantEmailNotification.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ParticipantEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantEmailService.class);
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm z")
            .withZone(ZoneId.of("UTC"));

    private final JavaMailSender mailSender;
    private final ApplicationEventPublisher eventPublisher;
    private final String fromAddress;
    private final String frontendUrl;

    public ParticipantEmailService(
            JavaMailSender mailSender,
            ApplicationEventPublisher eventPublisher,
            @Value("${ticket-war.mail.from}") String fromAddress,
            @Value("${ticket-war.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.eventPublisher = eventPublisher;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    public void bookingInvitation(
            String email,
            String sessionName,
            String claimToken,
            int batchNumber,
            int ticketNumber,
            java.time.Instant expiresAt) {
        eventPublisher.publishEvent(new ParticipantEmailNotification(
                Kind.BOOKING_INVITATION, email, sessionName, claimToken,
                batchNumber, ticketNumber, expiresAt));
    }

    public void ticketClaimed(
            String email,
            String sessionName,
            int batchNumber,
            int ticketNumber) {
        eventPublisher.publishEvent(new ParticipantEmailNotification(
                Kind.TICKET_CLAIMED, email, sessionName, null,
                batchNumber, ticketNumber, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ParticipantEmailNotification notification) {
        try {
            mailSender.send(toMessage(notification));
        } catch (MailException exception) {
            logger.warn("Could not deliver {} email; SMTP settings may need configuration.",
                    notification.kind());
        }
    }

    private SimpleMailMessage toMessage(ParticipantEmailNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(notification.recipient());

        switch (notification.kind()) {
            case BOOKING_INVITATION -> {
                message.setSubject("You can claim a Ticket War ticket");
                message.setText("A ticket is available for " + notification.sessionName() + ".\n\n"
                        + "Batch: " + notification.batchNumber() + "\n"
                        + "Ticket: " + notification.ticketNumber() + "\n"
                        + "Claim before: " + DEADLINE_FORMAT.format(notification.expiresAt()) + "\n\n"
                        + "Claim it using this secure link:\n"
                        + frontendUrl + "/claim#token=" + notification.token() + "\n\n"
                        + "The invitation expires at the time shown above.");
            }
            case TICKET_CLAIMED -> {
                message.setSubject("Your Ticket War ticket is confirmed");
                message.setText("Your ticket claim for " + notification.sessionName() + " is confirmed.\n\n"
                        + "Batch: " + notification.batchNumber() + "\n"
                        + "Ticket: " + notification.ticketNumber());
            }
        }
        return message;
    }
}
