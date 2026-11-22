package com.ticket_war.war.client;

import com.ticket_war.war.dto.response.EventDetailsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class EventServiceClient {

    private final RestClient restClient;

    public EventServiceClient(
            @Value("${ticket-war.gateway-base-url:http://localhost:8080}") String gatewayBaseUrl) {
        restClient = RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .build();
    }

    public EventDetailsResponse getEvent(UUID eventId) {
        try {
            EventDetailsResponse event = restClient.get()
                    .uri("/api/events/{id}", eventId)
                    .retrieve()
                    .body(EventDetailsResponse.class);
            if (event == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Event Service returned an empty response.");
            }
            return event;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Event not found: " + eventId);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Event Service could not verify the linked Event.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Event Service is unavailable. Please retry the request.");
        }
    }
}
