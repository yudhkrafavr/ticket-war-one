package com.ticket_war.war.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateWarBatchRequest {

    @NotNull
    @Positive
    private Integer participantCapacity;

}
