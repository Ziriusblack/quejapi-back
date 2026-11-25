package com.quejapp.quejapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeuralPriorityRequestDTO {
    private Integer type;
    private Integer category;
    private String department;
    private String subject;
    private String description;
    private String recievedDate;

    @JsonProperty("past_30d_tickets")
    private Integer past30dTickets;
}