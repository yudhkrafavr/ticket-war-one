package com.ticket_war.war.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ChallengeAnswerSubmission {

    @NotNull
    private UUID attemptQuestionId;

    @NotNull
    private UUID selectedOptionId;
}
