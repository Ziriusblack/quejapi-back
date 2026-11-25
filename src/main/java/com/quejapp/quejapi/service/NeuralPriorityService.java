package com.quejapp.quejapi.service;

import com.quejapp.quejapi.dto.NeuralPriorityRequestDTO;
import com.quejapp.quejapi.dto.NeuralPriorityResponseDTO;
import com.quejapp.quejapi.model.Complaint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class NeuralPriorityService {

    @Value("${nn.service.url:http://127.0.0.1:8000}")
    private String nnServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public NeuralPriorityResponseDTO predictPriority(NeuralPriorityRequestDTO request) {
        try {
            String baseUrl = nnServiceUrl.replaceAll("/+$", "");
            String endpoint = baseUrl + "/predict-priority";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<NeuralPriorityRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<NeuralPriorityResponseDTO> response = restTemplate.postForEntity(
                    endpoint,
                    entity,
                    NeuralPriorityResponseDTO.class
            );

            return response.getBody();
        } catch (Exception e) {
            System.err.println("Error llamando a NeuralPriorityService (" + nnServiceUrl + "): " + e.getMessage());
            // Respaldo seguro si el microservicio Python no está disponible
            return new NeuralPriorityResponseDTO(
                    "MEDIA",
                    0.0,
                    Map.of("BAJA", 0.0, "MEDIA", 0.0, "ALTA", 0.0)
            );
        }
    }

    public NeuralPriorityResponseDTO predictFromComplaint(Complaint complaint, int past30dTickets) {
        NeuralPriorityRequestDTO dto = NeuralPriorityRequestDTO.builder()
                .type(complaint.getType() != null ? complaint.getType() : 0)
                .category(complaint.getCategory() != null ? complaint.getCategory() : 0)
                .department(complaint.getDepartment() != null ? complaint.getDepartment() : "Gerencia")
                .subject(complaint.getSubject() != null ? complaint.getSubject() : "")
                .description(complaint.getDescription() != null ? complaint.getDescription() : "")
                .recievedDate(complaint.getRecievedDate() != null ? complaint.getRecievedDate().toString() : null)
                .past30dTickets(past30dTickets)
                .build();

        return predictPriority(dto);
    }
}