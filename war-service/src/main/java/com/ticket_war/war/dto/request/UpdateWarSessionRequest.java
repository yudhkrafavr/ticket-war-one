package com.ticket_war.war.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UpdateWarSessionRequest {

    @NotNull
    private UUID eventId;

    @NotBlank
    @Size(max = 150)
    private String name;

    private String description;

    @NotNull
    @Positive
    private Integer totalParticipants;

    @NotNull
    @Positive
    private Integer totalBatches;

    private Integer totalTickets;

    @NotNull
    @Positive
    private Integer questionsPerAttempt;

    @NotNull
    @Positive
    private Integer challengeDurationSeconds;

    private Instant registrationOpensAt;

    private Instant registrationClosesAt;

    private Instant waitingRoomOpensAt;

    private Instant challengeStartsAt;
}
