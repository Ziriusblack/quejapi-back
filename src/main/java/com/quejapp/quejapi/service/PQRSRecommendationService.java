package com.quejapp.quejapi.service;

import com.quejapp.quejapi.dto.PriorityComplaintDTO;
import com.quejapp.quejapi.model.Complaint;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PQRSRecommendationService {

    private final MongoTemplate mongoTemplate;

    public List<PriorityComplaintDTO> getPrioritizedComplaints() {
        return getPrioritizedComplaints(null, null);
    }

    public List<PriorityComplaintDTO> getPrioritizedComplaints(LocalDate startDate, LocalDate endDate) {

        // 1. Quejas activas (0 = Radicada, 1 = En Proceso) + filtro opcional de fechas
        Criteria criteria = Criteria.where("status").in(0, 1);

        if (startDate != null && endDate != null) {
            Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
            criteria.and("recievedDate").gte(start).lte(end);
        } else if (startDate != null) {
            Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            criteria.and("recievedDate").gte(start);
        } else if (endDate != null) {
            Date end = Date.from(endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
            criteria.and("recievedDate").lte(end);
        }

        Query query = new Query(criteria);

        // 2. Proyección ligera incluyendo los campos de la Red Neuronal
        query.fields().include(
                "id", "reference", "subject", "status",
                "category", "type", "recievedDate", "daysfromExpiration",
                "predictedPriority", "priorityConfidence"
        );

        List<Complaint> activeComplaints = mongoTemplate.find(query, Complaint.class);

        // 3. Procesamiento, mapeo y ordenamiento (Red Neuronal + Score operativo)
        return activeComplaints.stream()
                .map(c -> {
                    int score = calculatePriority(c);
                    String resolvedLevel = (c.getPredictedPriority() != null && !c.getPredictedPriority().isBlank())
                            ? c.getPredictedPriority().toUpperCase()
                            : getPriorityLevel(score);

                    return PriorityComplaintDTO.builder()
                            .id(c.getId())
                            .reference(c.getReference())
                            .subject(c.getSubject())
                            .priorityScore(score)
                            .priorityLevel(resolvedLevel)
                            .reason(generateReason(c, resolvedLevel, score))
                            .status(c.getStatus())
                            .category(c.getCategory())
                            .type(c.getType())
                            .daysOpen(calculateDaysOpen(c))
                            .predictedPriority(c.getPredictedPriority())
                            .priorityConfidence(c.getPriorityConfidence())
                            .build();
                })
                .sorted(Comparator
                        .comparingInt((PriorityComplaintDTO dto) -> priorityWeight(dto.getPriorityLevel())).reversed()
                        .thenComparing(PriorityComplaintDTO::getPriorityScore, Comparator.reverseOrder()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private int priorityWeight(String level) {
        if (level == null) return 0;
        return switch (level.toUpperCase()) {
            case "ALTA" -> 3;
            case "MEDIA" -> 2;
            case "BAJA" -> 1;
            default -> 0;
        };
    }

    private int calculatePriority(Complaint c) {
        int score = 0;
        int days = calculateDaysOpen(c);
        score += days * 2;

        if (c.getCategory() != null) {
            switch (c.getCategory()) {
                case 7: score += 40; break; // Seguridad
                case 6: score += 25; break; // Accesibilidad
                case 5: score += 20; break; // Atención cliente
            }
        }

        if (c.getType() != null) {
            switch (c.getType()) {
                case 2: score += 30; break; // Reclamo
                case 1: score += 20; break; // Queja
            }
        }

        if (c.getDaysfromExpiration() != null && c.getDaysfromExpiration() <= 3) {
            score += 50;
        }

        return score;
    }

    private int calculateDaysOpen(Complaint complaint) {
        if (complaint.getRecievedDate() == null) {
            return 0;
        }
        LocalDate received = complaint.getRecievedDate()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        return (int) ChronoUnit.DAYS.between(received, LocalDate.now());
    }

    private String getPriorityLevel(int score) {
        if (score >= 80) return "ALTA";
        if (score >= 40) return "MEDIA";
        return "BAJA";
    }

    private String generateReason(Complaint c, String level, int score) {
        if ("ALTA".equals(level) || score >= 80) return "Prioridad alta: requiere atención inmediata";
        if ("MEDIA".equals(level) || score >= 40) return "Prioridad media: requiere seguimiento prioritario";
        return "Prioridad baja: puede atenderse en flujo normal";
    }
}