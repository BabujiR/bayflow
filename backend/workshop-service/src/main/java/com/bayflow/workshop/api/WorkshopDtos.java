package com.bayflow.workshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;

import com.bayflow.workshop.application.JobCardService.OpenCommand;
import com.bayflow.workshop.application.ServiceRecord;
import com.bayflow.workshop.domain.ApprovalStatus;
import com.bayflow.workshop.domain.InspectionFinding;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;
import com.bayflow.workshop.domain.LabourEntry;
import com.bayflow.workshop.domain.PartUsage;
import com.bayflow.workshop.domain.RecommendedWorkItem;
import com.bayflow.workshop.domain.ServiceHistoryEntry;
import com.bayflow.workshop.domain.Severity;
import com.bayflow.workshop.domain.WorkLog;

/** Request and response bodies of the workshop API. */
public final class WorkshopDtos {

    private WorkshopDtos() {
    }

    // ---- requests -------------------------------------------------------------------

    /** Sent by appointment-service at check-in (or by an advisor). */
    public record OpenJobCardRequest(
            @NotNull UUID appointmentId,
            @NotNull UUID vehicleId,
            @NotBlank @Size(max = 17) String vin,
            @NotBlank @Size(max = 20) String registration,
            UUID customerUserId,
            @NotBlank @Size(max = 100) String customerName,
            @NotNull @PositiveOrZero Integer mileage) {

        OpenCommand toCommand() {
            return new OpenCommand(appointmentId, vehicleId, vin, registration, customerUserId, customerName,
                    mileage);
        }
    }

    public record FindingRequest(@NotBlank @Size(max = 1000) String description, @NotNull Severity severity) {
    }

    public record RecommendationRequest(
            @NotBlank @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal estimatedPartsCost,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2)
            BigDecimal estimatedLabourHours) {
    }

    public record WorkLogRequest(@NotNull UUID recommendationId, @NotBlank @Size(max = 1000) String description) {
    }

    public record PartRequest(
            @NotNull UUID recommendationId,
            @NotBlank @Size(max = 50) String partNumber,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    /** {@code hourlyRate} is optional; the workshop rate is used when it is missing. */
    public record LabourRequest(
            @NotNull UUID recommendationId,
            @NotNull @DecimalMin("0.01") @DecimalMax("24.00") @Digits(integer = 2, fraction = 2) BigDecimal hours,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal hourlyRate) {
    }

    // ---- responses ------------------------------------------------------------------

    public record WorkLogResponse(UUID id, UUID recommendationId, String mechanic, String description,
            Instant completedAt) {

        static WorkLogResponse from(WorkLog w) {
            return new WorkLogResponse(w.getId(), w.getRecommendation().getId(), w.getMechanic(), w.getDescription(),
                    w.getCompletedAt());
        }
    }

    public record PartResponse(UUID id, UUID recommendationId, String partNumber, String name, int quantity,
            BigDecimal unitPrice, BigDecimal lineTotal, String recordedBy) {

        static PartResponse from(PartUsage p) {
            return new PartResponse(p.getId(), p.getRecommendation().getId(), p.getPartNumber(), p.getName(),
                    p.getQuantity(), p.getUnitPrice(), p.lineTotal(), p.getRecordedBy());
        }
    }

    public record LabourResponse(UUID id, UUID recommendationId, String mechanic, BigDecimal hours,
            BigDecimal hourlyRate, BigDecimal cost) {

        static LabourResponse from(LabourEntry l) {
            return new LabourResponse(l.getId(), l.getRecommendation().getId(), l.getMechanic(), l.getHours(),
                    l.getHourlyRate(), l.cost());
        }
    }

    public record FindingResponse(UUID id, String description, Severity severity, String recordedBy,
            Instant createdAt) {

        static FindingResponse from(InspectionFinding f) {
            return new FindingResponse(f.getId(), f.getDescription(), f.getSeverity(), f.getRecordedBy(),
                    f.getCreatedAt());
        }
    }

    public record RecommendationResponse(UUID id, String description, BigDecimal estimatedPartsCost,
            BigDecimal estimatedLabourHours, BigDecimal estimatedLabourCost, BigDecimal estimatedTotal,
            ApprovalStatus approvalStatus, String decidedBy, Instant decidedAt) {

        static RecommendationResponse from(RecommendedWorkItem i, BigDecimal rate) {
            return new RecommendationResponse(i.getId(), i.getDescription(), i.getEstimatedPartsCost(),
                    i.getEstimatedLabourHours(), i.estimatedLabourCost(rate), i.estimatedTotal(rate),
                    i.getApprovalStatus(), i.getDecidedBy(), i.getDecidedAt());
        }
    }

    /**
     * Full job card: estimates the customer approves against, the work recorded, and (once
     * COMPLETED) the final totals.
     */
    public record JobCardResponse(UUID id, UUID appointmentId, UUID vehicleId, String vin, String registration,
            UUID customerUserId, String customerName, int mileage, JobCardStatus status,
            List<FindingResponse> findings, List<RecommendationResponse> recommendations,
            List<WorkLogResponse> workLogs, List<PartResponse> parts, List<LabourResponse> labour,
            BigDecimal labourRate, String currency, BigDecimal estimatedTotal, BigDecimal approvedEstimate,
            BigDecimal partsTotal, BigDecimal labourTotal, BigDecimal grandTotal,
            Instant submittedAt, Instant decidedAt, Instant startedAt, Instant completedAt,
            Instant createdAt, Instant updatedAt) {

        static JobCardResponse from(JobCard c, BigDecimal rate, String currency) {
            return new JobCardResponse(c.getId(), c.getAppointmentId(), c.getVehicleId(), c.getVin(),
                    c.getRegistration(), c.getCustomerUserId(), c.getCustomerName(), c.getMileage(), c.getStatus(),
                    c.getFindings().stream().map(FindingResponse::from).toList(),
                    c.getRecommendations().stream().map(i -> RecommendationResponse.from(i, rate)).toList(),
                    c.getWorkLogs().stream().map(WorkLogResponse::from).toList(),
                    c.getParts().stream().map(PartResponse::from).toList(),
                    c.getLabour().stream().map(LabourResponse::from).toList(),
                    rate, currency, c.estimatedTotal(rate), c.approvedEstimate(rate),
                    c.getPartsTotal(), c.getLabourTotal(), c.getGrandTotal(),
                    c.getSubmittedAt(), c.getDecidedAt(), c.getStartedAt(), c.getCompletedAt(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    /** One completed job in a vehicle's service history. */
    public record ServiceHistoryResponse(UUID id, UUID vehicleId, UUID jobCardId, UUID appointmentId, String vin,
            String registration, Instant serviceDate, int mileage, BigDecimal partsTotal, BigDecimal labourTotal,
            BigDecimal totalCost, String currency, ServiceRecord details) {

        static ServiceHistoryResponse from(ServiceHistoryEntry e, ServiceRecord details, String currency) {
            return new ServiceHistoryResponse(e.getId(), e.getVehicleId(), e.getJobCardId(), e.getAppointmentId(),
                    e.getVin(), e.getRegistration(), e.getServiceDate(), e.getMileage(), e.getPartsTotal(),
                    e.getLabourTotal(), e.getTotalCost(), currency, details);
        }
    }

    /** Job card row for lists (no findings or recommendations). */
    public record JobCardSummary(UUID id, UUID appointmentId, UUID vehicleId, String vin, String registration,
            String customerName, int mileage, JobCardStatus status, Instant createdAt, Instant updatedAt) {

        static JobCardSummary from(JobCard c) {
            return new JobCardSummary(c.getId(), c.getAppointmentId(), c.getVehicleId(), c.getVin(),
                    c.getRegistration(), c.getCustomerName(), c.getMileage(), c.getStatus(), c.getCreatedAt(),
                    c.getUpdatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
