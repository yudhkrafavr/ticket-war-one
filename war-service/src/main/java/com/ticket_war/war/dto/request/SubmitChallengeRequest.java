package com.ticket_war.war.dto.request;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SubmitChallengeRequest {

    private List<@Valid ChallengeAnswerSubmission> answers;
}
