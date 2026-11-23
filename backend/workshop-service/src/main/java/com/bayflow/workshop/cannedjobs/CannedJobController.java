package com.bayflow.workshop.cannedjobs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.cannedjobs.CannedJob.Details;
import com.bayflow.workshop.cannedjobs.CannedJob.PartDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Canned jobs: managers and admins keep the list; advisors and mechanics use it on job cards. */
@RestController
@RequestMapping("/api/v1/canned-jobs")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Canned jobs")
public class CannedJobController {

    public record PartRequest(
            @NotBlank @Size(max = 50) String partNumber,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
            BigDecimal unitPrice) {
    }

    /** version: required to update (the one read). locationId null = every location. */
    public record CannedJobRequest(
            UUID locationId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) String category,
            @NotBlank @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal labourHours,
            @NotNull @Size(max = 50) List<@Valid PartRequest> parts,
            Long version) {

        Details details() {
            return new Details(locationId, name, category, description, labourHours, parts.stream()
                    .map(p -> new PartDetails(p.partNumber(), p.name(), p.quantity(), p.unitPrice())).toList());
        }
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record PartResponse(UUID id, String partNumber, String name, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static PartResponse from(CannedJobPart p) {
            return new PartResponse(p.getId(), p.getPartNumber(), p.getName(), p.getQuantity(), p.getUnitPrice(),
                    p.lineTotal());
        }
    }

    /** partsTotal: what the usual parts come to, the parts estimate of a recommendation. */
    public record CannedJobResponse(UUID id, UUID locationId, String name, String category, String description,
            BigDecimal labourHours, List<PartResponse> parts, BigDecimal partsTotal, boolean active,
            Instant updatedAt, long version) {

        static CannedJobResponse from(CannedJob j) {
            return new CannedJobResponse(j.getId(), j.getLocationId(), j.getName(), j.getCategory(),
                    j.getDescription(), j.getLabourHours(), j.getParts().stream().map(PartResponse::from).toList(),
                    j.partsTotal(), j.isActive(), j.getUpdatedAt(), j.getVersion() == null ? 0 : j.getVersion());
        }
    }

    private final CannedJobService jobs;

    public CannedJobController(CannedJobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Canned jobs by category and name",
            description = "locationId: only those usable there; q: in name, category or description; "
                    + "includeInactive: archived ones too (for the settings page).")
    public List<CannedJobResponse> list(@RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return jobs.list(locationId, q, includeInactive).stream().map(CannedJobResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN')")
    @Operation(summary = "One canned job with its usual parts")
    public CannedJobResponse get(@PathVariable UUID id) {
        return CannedJobResponse.from(jobs.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a canned job (managers and admins)")
    public CannedJobResponse create(@Valid @RequestBody CannedJobRequest request, Authentication auth) {
        return CannedJobResponse.from(jobs.create(CurrentUser.from(auth), request.details()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Change a canned job (send the version you read; 409 if stale)")
    public CannedJobResponse update(@PathVariable UUID id, @Valid @RequestBody CannedJobRequest request) {
        if (request.version() == null) {
            throw com.bayflow.workshop.application.ApiException.badRequest("version-required",
                    "Send the version you read.");
        }
        return CannedJobResponse.from(jobs.update(id, request.version(), request.details()));
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Archive (false) or restore (true) a canned job")
    public CannedJobResponse setActive(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request) {
        return CannedJobResponse.from(jobs.setActive(id, request.active()));
    }
}
