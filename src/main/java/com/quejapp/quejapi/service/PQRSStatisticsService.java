package com.quejapp.quejapi.service;

import com.quejapp.quejapi.dto.PQRSStatisticsDTO;
import com.quejapp.quejapi.model.Complaint;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PQRSStatisticsService {

        private final MongoTemplate mongoTemplate;
        private final int EXPIRATION_DAYS = 15;

        @Cacheable(value = "dashboardStats")
        public PQRSStatisticsDTO getStatistics(LocalDate startDate, LocalDate endDate) {
                Criteria baseCriteria = buildDateCriteria(startDate, endDate);

                // 1. CONTEOS DIRECTOS EN BD (Operaciones O(1) ultra rápidas)
                long totalPqrs = mongoTemplate.count(new Query(baseCriteria), Complaint.class);

                if (totalPqrs == 0) {
                        return PQRSStatisticsDTO.builder()
                                .totalPqrs(0L).openPqrs(0L).closedPqrs(0L).inProgressPqrs(0L)
                                .build();
                }

                long openPqrs = countStatus(baseCriteria, 0);
                long inProgressPqrs = countStatus(baseCriteria, 1);
                long closedPqrs = mongoTemplate.count(new Query(new Criteria().andOperator(baseCriteria, Criteria.where("status").in(2, 3))), Complaint.class);

                // 2. AGREGACIONES (MongoDB agrupa los datos, Java no gasta memoria)
                Map<String, Long> pqrsByStatus = getGroupedCounts(baseCriteria, "status", getStatusNamesMap());
                Map<String, Long> pqrsByType = getGroupedCounts(baseCriteria, "type", getTypeNamesMap());
                Map<String, Long> pqrsByCategory = getGroupedCounts(baseCriteria, "category", getCategoryNamesMap());

                // 3. MÉTRICAS DE RESOLUCIÓN (Avg, Min, Max directo en BD)
                ResolutionStats resStats = getResolutionMetrics(baseCriteria);
                long satisfactory = mongoTemplate.count(new Query(new Criteria().andOperator(baseCriteria, Criteria.where("status").in(2, 3), Criteria.where("daysToResolve").lte(15))), Complaint.class);

                double resolutionRate = (closedPqrs * 100.0) / totalPqrs;
                double satisfactionRate = closedPqrs > 0 ? (satisfactory * 100.0) / closedPqrs : 0.0;

                // 4. CÁLCULO DE VENCIMIENTOS (Comparación de fechas en BD)
                long expiredPqrs = countExpired(baseCriteria);
                long nearExpirationPqrs = countNearExpiration(baseCriteria);

                // 5. PROYECCIÓN LIGERA (Trae solo lo necesario para agrupar empleados y meses, ahorrando 95% de RAM)
                List<Complaint> lightweightComplaints = getLightweightComplaints(baseCriteria);

                return PQRSStatisticsDTO.builder()
                        .totalPqrs(totalPqrs)
                        .openPqrs(openPqrs)
                        .closedPqrs(closedPqrs)
                        .inProgressPqrs(inProgressPqrs)
                        .pqrsByStatus(pqrsByStatus)
                        .pqrsByType(pqrsByType)
                        .pqrsByCategory(pqrsByCategory)
                        .averageDaysToResolve(resStats.avg)
                        .minDaysToResolve(resStats.min)
                        .maxDaysToResolve(resStats.max)
                        .expiredPqrs(expiredPqrs)
                        .nearExpirationPqrs(nearExpirationPqrs)
                        .averageDaysFromExpiration(calculateAverageDaysFromExpiration(lightweightComplaints))
                        .topEmployees(calculateEmployeeStats(lightweightComplaints))
                        .monthlyTrends(calculateMonthlyTrends(lightweightComplaints))
                        .resolutionRate(Math.round(resolutionRate * 100.0) / 100.0)
                        .satisfactionRate(Math.round(satisfactionRate * 100.0) / 100.0)
                        .build();
        }

        // --- MÉTODOS DE BASE DE DATOS OPTIMIZADOS ---

        private Criteria buildDateCriteria(LocalDate startDate, LocalDate endDate) {
                Criteria criteria = new Criteria();
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
                return criteria;
        }

        private long countStatus(Criteria baseCriteria, int status) {
                return mongoTemplate.count(new Query(new Criteria().andOperator(baseCriteria, Criteria.where("status").is(status))), Complaint.class);
        }

        private long countExpired(Criteria baseCriteria) {
                Date fifteenDaysAgo = Date.from(LocalDate.now().minusDays(EXPIRATION_DAYS).atStartOfDay(ZoneId.systemDefault()).toInstant());
                Query q = new Query(new Criteria().andOperator(baseCriteria, Criteria.where("status").nin(2, 3), Criteria.where("recievedDate").lt(fifteenDaysAgo)));
                return mongoTemplate.count(q, Complaint.class);
        }

        private long countNearExpiration(Criteria baseCriteria) {
                Date fifteenDaysAgo = Date.from(LocalDate.now().minusDays(EXPIRATION_DAYS).atStartOfDay(ZoneId.systemDefault()).toInstant());
                Date twelveDaysAgo = Date.from(LocalDate.now().minusDays(EXPIRATION_DAYS - 3).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
                Query q = new Query(new Criteria().andOperator(baseCriteria, Criteria.where("status").nin(2, 3), Criteria.where("recievedDate").gte(fifteenDaysAgo).lte(twelveDaysAgo)));
                return mongoTemplate.count(q, Complaint.class);
        }

        private Map<String, Long> getGroupedCounts(Criteria baseCriteria, String field, Map<Integer, String> namesMap) {
                Aggregation agg = Aggregation.newAggregation(
                        Aggregation.match(baseCriteria),
                        Aggregation.group(field).count().as("count")
                );
                AggregationResults<Document> results = mongoTemplate.aggregate(agg, "quejas", Document.class);
                Map<String, Long> map = new HashMap<>();

                for (Document doc : results.getMappedResults()) {
                        Object id = doc.get("_id");
                        Integer key = null;
                        if (id instanceof Integer) {
                                key = (Integer) id;
                        } else if (id != null) {
                                try { key = Integer.parseInt(id.toString()); } catch (Exception e) {}
                        }

                        // Si la clave es nula o no existe, se asigna una etiqueta por defecto sin romper la app
                        String label = (key != null && namesMap.containsKey(key)) ? namesMap.get(key) : "Sin Categoría / Otro";
                        map.put(label, map.getOrDefault(label, 0L) + doc.getInteger("count").longValue());
                }
                return map;
        }

        private static class ResolutionStats {
                double avg = 0.0;
                int min = 0;
                int max = 0;
        }

        private ResolutionStats getResolutionMetrics(Criteria baseCriteria) {
                ResolutionStats stats = new ResolutionStats();
                Aggregation agg = Aggregation.newAggregation(
                        Aggregation.match(new Criteria().andOperator(baseCriteria, Criteria.where("status").in(2, 3), Criteria.where("daysToResolve").ne(null))),
                        Aggregation.group().avg("daysToResolve").as("avg").min("daysToResolve").as("min").max("daysToResolve").as("max")
                );
                AggregationResults<Document> results = mongoTemplate.aggregate(agg, "quejas", Document.class);
                if (!results.getMappedResults().isEmpty()) {
                        Document doc = results.getMappedResults().get(0);
                        stats.avg = doc.get("avg") != null ? ((Number) doc.get("avg")).doubleValue() : 0.0;
                        stats.min = doc.get("min") != null ? ((Number) doc.get("min")).intValue() : 0;
                        stats.max = doc.get("max") != null ? ((Number) doc.get("max")).intValue() : 0;
                }
                return stats;
        }

        private List<Complaint> getLightweightComplaints(Criteria baseCriteria) {
                Query query = new Query(baseCriteria);

                // ✅ SOLUCIÓN: Usar exclusivamente inclusiones (eliminamos el .exclude())
                query.fields()
                        .include("id", "status", "daysToResolve", "daysfromExpiration", "recievedDate", "employee", "employeeProfile");

                return mongoTemplate.find(query, Complaint.class);
        }

        // --- MÉTODOS DE MAPEO Y LÓGICA DE NEGOCIO (PROYECCIÓN) ---

        private Double calculateAverageDaysFromExpiration(List<Complaint> complaints) {
                return complaints.stream()
                        .filter(c -> c.getDaysfromExpiration() != null)
                        .mapToInt(Complaint::getDaysfromExpiration)
                        .average()
                        .orElse(0.0);
        }

        private boolean isStatusClosed(Integer status) {
                return status != null && (status == 3 || status == 2);
        }

        private List<PQRSStatisticsDTO.EmployeeStatsDTO> calculateEmployeeStats(List<Complaint> complaints) {
                Map<String, List<Complaint>> byEmployee = complaints.stream()
                        .filter(c -> c.getEmployee() != null && c.getEmployeeProfile() != null)
                        .collect(Collectors.groupingBy(Complaint::getEmployee));

                return byEmployee.entrySet().stream().map(entry -> {
                                List<Complaint> empComplaints = entry.getValue();
                                Complaint sample = empComplaints.get(0);
                                long totalResolved = empComplaints.stream().filter(c -> isStatusClosed(c.getStatus())).count();
                                double avgResTime = empComplaints.stream()
                                        .filter(c -> c.getDaysToResolve() != null && isStatusClosed(c.getStatus()))
                                        .mapToInt(Complaint::getDaysToResolve).average().orElse(0.0);
                                double resolutionRate = empComplaints.isEmpty() ? 0.0 : (totalResolved * 100.0) / empComplaints.size();

                                return PQRSStatisticsDTO.EmployeeStatsDTO.builder()
                                        .employeeId(entry.getKey())
                                        .employeeName(sample.getEmployeeProfile().getName() + " " + sample.getEmployeeProfile().getLastname())
                                        .employeeEmail(sample.getEmployeeProfile().getEmail())
                                        .totalAssigned((long) empComplaints.size())
                                        .totalResolved(totalResolved)
                                        .averageResolutionTime(Math.round(avgResTime * 100.0) / 100.0)
                                        .resolutionRate(Math.round(resolutionRate * 100.0) / 100.0)
                                        .build();
                        }).sorted(Comparator.comparing(PQRSStatisticsDTO.EmployeeStatsDTO::getTotalResolved).reversed())
                        .limit(10).collect(Collectors.toList());
        }

        private List<PQRSStatisticsDTO.MonthlyTrendDTO> calculateMonthlyTrends(List<Complaint> complaints) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
                Map<String, List<Complaint>> byMonth = complaints.stream()
                        .filter(c -> c.getRecievedDate() != null)
                        .collect(Collectors.groupingBy(c -> c.getRecievedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter)));

                return byMonth.entrySet().stream().map(entry -> {
                        List<Complaint> monthComplaints = entry.getValue();
                        long resolved = monthComplaints.stream().filter(c -> isStatusClosed(c.getStatus())).count();
                        double avgResTime = monthComplaints.stream()
                                .filter(c -> c.getDaysToResolve() != null && isStatusClosed(c.getStatus()))
                                .mapToInt(Complaint::getDaysToResolve).average().orElse(0.0);

                        return PQRSStatisticsDTO.MonthlyTrendDTO.builder()
                                .month(entry.getKey())
                                .totalReceived((long) monthComplaints.size())
                                .totalResolved(resolved)
                                .averageResolutionTime(Math.round(avgResTime * 100.0) / 100.0)
                                .build();
                }).sorted(Comparator.comparing(PQRSStatisticsDTO.MonthlyTrendDTO::getMonth)).collect(Collectors.toList());
        }

        // Mapas seguros que toleran nulos y valores atípicos
        private Map<Integer, String> getStatusNamesMap() {
                Map<Integer, String> map = new HashMap<>();
                map.put(0, "Radicada");
                map.put(1, "En Proceso");
                map.put(2, "Resuelta");
                map.put(3, "Cerrada");
                return map;
        }

        private Map<Integer, String> getTypeNamesMap() {
                Map<Integer, String> map = new HashMap<>();
                map.put(0, "Petición");
                map.put(1, "Queja");
                map.put(2, "Reclamo");
                map.put(3, "Sugerencia");
                map.put(4, "Felicitación");
                return map;
        }

        private Map<Integer, String> getCategoryNamesMap() {
                Map<Integer, String> map = new HashMap<>();
                map.put(0, "Servicio de Buses");
                map.put(1, "Trarifas y Pagos");
                map.put(2, "Horarios");
                map.put(3, "Rutas");
                map.put(4, "infraestructura");
                map.put(5, "Atención al Cliente");
                map.put(6, "Accesibilidad");
                map.put(7, "Seguridad");
                map.put(8, "Otros");
                return map;
        }
}