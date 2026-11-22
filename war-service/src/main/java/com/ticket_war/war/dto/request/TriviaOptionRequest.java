package com.ticket_war.war.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TriviaOptionRequest {

    @NotBlank
    @Size(max = 500)
    private String optionText;

    @NotNull
    @Positive
    private Integer optionOrder;

    @NotNull
    private Boolean isCorrect;
}
