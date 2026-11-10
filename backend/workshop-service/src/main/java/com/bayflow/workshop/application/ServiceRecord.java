package com.bayflow.workshop.application;

import java.math.BigDecimal;
import java.util.List;

import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.RecommendedWorkItem;

/**
 * What a completed job contained, frozen into the service history: what the inspection
 * found, the work performed for each approved item, declined items, parts and labour.
 */
public record ServiceRecord(List<Finding> findings, List<Work> work, List<String> declined, List<Part> parts,
        List<Labour> labour) {

    public record Finding(String description, String severity) {
    }

    public record Work(String recommendation, List<String> workPerformed, List<String> mechanics) {
    }

    public record Part(String recommendation, String partNumber, String name, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public record Labour(String recommendation, String mechanic, BigDecimal hours, BigDecimal hourlyRate,
            BigDecimal cost) {
    }

    public static ServiceRecord of(JobCard card) {
        List<Finding> findings = card.getFindings().stream()
                .map(f -> new Finding(f.getDescription(), f.getSeverity().name()))
                .toList();
        List<Work> work = card.getRecommendations().stream()
                .filter(RecommendedWorkItem::isApproved)
                .map(item -> new Work(item.getDescription(),
                        card.getWorkLogs().stream().filter(l -> l.getRecommendation() == item)
                                .map(l -> l.getDescription()).toList(),
                        card.getWorkLogs().stream().filter(l -> l.getRecommendation() == item)
                                .map(l -> l.getMechanic()).distinct().toList()))
                .toList();
        List<String> declined = card.getRecommendations().stream()
                .filter(item -> !item.isApproved())
                .map(RecommendedWorkItem::getDescription)
                .toList();
        List<Part> parts = card.getParts().stream()
                .map(p -> new Part(p.getRecommendation().getDescription(), p.getPartNumber(), p.getName(),
                        p.getQuantity(), p.getUnitPrice(), p.lineTotal()))
                .toList();
        List<Labour> labour = card.getLabour().stream()
                .map(l -> new Labour(l.getRecommendation().getDescription(), l.getMechanic(), l.getHours(),
                        l.getHourlyRate(), l.cost()))
                .toList();
        return new ServiceRecord(findings, work, declined, parts, labour);
    }
}
