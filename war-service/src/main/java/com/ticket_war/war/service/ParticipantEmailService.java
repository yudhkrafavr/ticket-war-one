package com.ticket_war.war.service;

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


@Service
public class ParticipantEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantEmailService.class);
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

    public void registrationAccess(String email, String sessionName, String accessToken) {
        eventPublisher.publishEvent(new ParticipantEmailNotification(email, sessionName, accessToken));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ParticipantEmailNotification notification) {
        try {
            mailSender.send(toMessage(notification));
        } catch (MailException exception) {
            logger.warn("Could not deliver participant registration email; SMTP settings may need configuration.");
        }
    }

    private SimpleMailMessage toMessage(ParticipantEmailNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(notification.recipient());
        message.setSubject("Your Ticket War participant access");
        message.setText("You are registered for " + notification.sessionName() + ".\n\n"
                + "Open your participant session using this secure link:\n"
                + frontendUrl + "/access#token=" + notification.accessToken() + "\n\n"
                + "Keep this link private. It grants access to your participant session.");
        return message;
    }
}
