package com.quejapp.quejapi.controller;

import com.quejapp.quejapi.dto.NeuralPriorityRequestDTO;
import com.quejapp.quejapi.dto.NeuralPriorityResponseDTO;
import com.quejapp.quejapi.service.NeuralPriorityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/neural-priority")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NeuralPriorityController {

    private final NeuralPriorityService neuralPriorityService;

    @PostMapping("/predict")
    public ResponseEntity<NeuralPriorityResponseDTO> predict(@RequestBody NeuralPriorityRequestDTO request) {
        NeuralPriorityResponseDTO prediction = neuralPriorityService.predictPriority(request);
        return ResponseEntity.ok(prediction);
    }
}