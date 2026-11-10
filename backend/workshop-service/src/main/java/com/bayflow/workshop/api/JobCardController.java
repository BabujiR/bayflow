package com.bayflow.workshop.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.api.WorkshopDtos.FindingRequest;
import com.bayflow.workshop.api.WorkshopDtos.JobCardResponse;
import com.bayflow.workshop.api.WorkshopDtos.JobCardSummary;
import com.bayflow.workshop.api.WorkshopDtos.LabourRequest;
import com.bayflow.workshop.api.WorkshopDtos.OpenJobCardRequest;
import com.bayflow.workshop.api.WorkshopDtos.PageResponse;
import com.bayflow.workshop.api.WorkshopDtos.PartRequest;
import com.bayflow.workshop.api.WorkshopDtos.RecommendationRequest;
import com.bayflow.workshop.api.WorkshopDtos.WorkLogRequest;
import com.bayflow.workshop.application.JobCardService;
import com.bayflow.workshop.application.JobCardService.OpenResult;
import com.bayflow.workshop.config.WorkshopProperties;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.JobCardStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/job-cards")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Job cards")
public class JobCardController {

    private final JobCardService jobCards;
    private final WorkshopProperties properties;

    public JobCardController(JobCardService jobCards, WorkshopProperties properties) {
        this.jobCards = jobCards;
        this.properties = properties;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYSTEM', 'SERVICE_ADVISOR')")
    @Operation(summary = "Open the job card for a checked-in appointment",
            description = "Idempotent on appointmentId: 201 when created, 200 with the existing card on a repeat.")
    public ResponseEntity<JobCardResponse> open(@Valid @RequestBody OpenJobCardRequest request) {
        OpenResult result;
        try {
            result = jobCards.open(request.toCommand());
        } catch (DataIntegrityViolationException raceLost) {
            // A concurrent request created it first; return that card.
            JobCard existing = jobCards.findByAppointment(request.appointmentId()).orElseThrow(() -> raceLost);
            result = new OpenResult(existing, false);
        }
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(response(result.jobCard()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "List job cards, newest first (customers see their own)")
    public PageResponse<JobCardSummary> list(@RequestParam(required = false) JobCardStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return PageResponse.from(jobCards.list(CurrentUser.from(auth), status, Paging.page(page), Paging.size(size)),
                JobCardSummary::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "A job card with findings, recommendations and estimates")
    public JobCardResponse get(@PathVariable UUID id, Authentication auth) {
        return response(jobCards.get(CurrentUser.from(auth), id));
    }

    @GetMapping("/by-appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN', 'SYSTEM')")
    @Operation(summary = "The job card opened for an appointment")
    public JobCardResponse byAppointment(@PathVariable UUID appointmentId, Authentication auth) {
        return response(jobCards.getByAppointment(CurrentUser.from(auth), appointmentId));
    }

    @PostMapping("/{id}/findings")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MECHANIC')")
    @Operation(summary = "Record an inspection finding (OPEN/INSPECTED; the first moves OPEN → INSPECTED)")
    public JobCardResponse addFinding(@PathVariable UUID id, @Valid @RequestBody FindingRequest request,
            Authentication auth) {
        return response(jobCards.addFinding(CurrentUser.from(auth), id, request.description(), request.severity()));
    }

    @PostMapping("/{id}/recommendations")
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "Recommend work with a parts and labour estimate (OPEN/INSPECTED)")
    public JobCardResponse addRecommendation(@PathVariable UUID id,
            @Valid @RequestBody RecommendationRequest request) {
        return response(jobCards.addRecommendation(id, request.description(), request.estimatedPartsCost(),
                request.estimatedLabourHours()));
    }

    @PostMapping("/{id}/submit-for-approval")
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "INSPECTED → AWAITING_APPROVAL (or DECLINED when nothing is recommended)")
    public JobCardResponse submit(@PathVariable UUID id) {
        return response(jobCards.submitForApproval(id));
    }

    @PostMapping("/{id}/recommendations/{itemId}/approve")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Approve one recommendation (customer, or advisor on the customer's behalf)")
    public JobCardResponse approve(@PathVariable UUID id, @PathVariable UUID itemId, Authentication auth) {
        return response(jobCards.decide(CurrentUser.from(auth), id, itemId, true));
    }

    @PostMapping("/{id}/recommendations/{itemId}/decline")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Decline one recommendation (customer, or advisor on the customer's behalf)")
    public JobCardResponse decline(@PathVariable UUID id, @PathVariable UUID itemId, Authentication auth) {
        return response(jobCards.decide(CurrentUser.from(auth), id, itemId, false));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('MECHANIC')")
    @Operation(summary = "APPROVED/PARTIALLY_APPROVED → IN_PROGRESS")
    public JobCardResponse start(@PathVariable UUID id) {
        return response(jobCards.start(id));
    }

    @PostMapping("/{id}/work-logs")
    @PreAuthorize("hasRole('MECHANIC')")
    @Operation(summary = "Log work done on an approved recommendation (IN_PROGRESS)")
    public JobCardResponse logWork(@PathVariable UUID id, @Valid @RequestBody WorkLogRequest request,
            Authentication auth) {
        return response(jobCards.logWork(CurrentUser.from(auth), id, request.recommendationId(),
                request.description()));
    }

    @PostMapping("/{id}/parts")
    @PreAuthorize("hasRole('MECHANIC')")
    @Operation(summary = "Record a part used for an approved recommendation (IN_PROGRESS)")
    public JobCardResponse addPart(@PathVariable UUID id, @Valid @RequestBody PartRequest request,
            Authentication auth) {
        return response(jobCards.addPart(CurrentUser.from(auth), id, request.recommendationId(),
                request.partNumber(), request.name(), request.quantity(), request.unitPrice()));
    }

    @PostMapping("/{id}/labour")
    @PreAuthorize("hasRole('MECHANIC')")
    @Operation(summary = "Record labour hours for an approved recommendation (IN_PROGRESS)",
            description = "hourlyRate is optional and defaults to the workshop labour rate.")
    public JobCardResponse addLabour(@PathVariable UUID id, @Valid @RequestBody LabourRequest request,
            Authentication auth) {
        return response(jobCards.addLabour(CurrentUser.from(auth), id, request.recommendationId(),
                request.hours(), request.hourlyRate()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MECHANIC')")
    @Operation(summary = "IN_PROGRESS/DECLINED → COMPLETED; calculates totals and writes the service history")
    public JobCardResponse complete(@PathVariable UUID id) {
        return response(jobCards.complete(id));
    }

    private JobCardResponse response(JobCard card) {
        return JobCardResponse.from(card, properties.getLabourRate(), properties.getCurrency());
    }
}
