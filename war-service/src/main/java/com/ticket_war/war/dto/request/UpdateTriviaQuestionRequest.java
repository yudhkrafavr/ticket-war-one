package com.ticket_war.war.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UpdateTriviaQuestionRequest {

    @NotBlank
    private String questionText;

    @NotNull
    @Positive
    private Integer questionOrder;

    @NotNull
    @Size(min = 2)
    private List<@Valid TriviaOptionRequest> options;
}
