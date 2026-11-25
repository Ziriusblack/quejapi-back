package com.quejapp.quejapi.controller;

import com.quejapp.quejapi.dto.PriorityComplaintDTO;
import com.quejapp.quejapi.service.PQRSRecommendationService;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class PQRSRecommendationController {

    private final PQRSRecommendationService recommendationService;

    @GetMapping("/priorities")
    public ResponseEntity<List<PriorityComplaintDTO>> getPrioritizedComplaints(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(recommendationService.getPrioritizedComplaints(startDate, endDate));
    }
}