package com.quejapp.quejapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NeuralPriorityResponseDTO {
    private String priority;
    private Double confidence;
    private Map<String, Double> probabilities;
}