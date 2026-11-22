package com.ticketwar.event_service.service;

import com.ticketwar.event_service.dto.booking.WarResultsFinalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WarResultsFinalizedListener {

    private static final Logger logger = LoggerFactory.getLogger(WarResultsFinalizedListener.class);

    private final JsonMapper jsonMapper;
    private final BookingService bookingService;

    public WarResultsFinalizedListener(JsonMapper jsonMapper, BookingService bookingService) {
        this.jsonMapper = jsonMapper;
        this.bookingService = bookingService;
    }

    @KafkaListener(
            topics = "${ticket-war.kafka.war-results-finalized-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String json) {
        try {
            WarResultsFinalizedMessage event = jsonMapper.readValue(json, WarResultsFinalizedMessage.class);
            bookingService.handleResultsFinalized(event);
            logger.info("Processed finalized results event {} for War Session {}",
                    event.eventMessageId(), event.warSessionId());
        } catch (Exception exception) {
            logger.error("Could not process WAR_RESULTS_FINALIZED message", exception);
            throw new IllegalStateException("Could not process finalized War results message.", exception);
        }
    }
}
