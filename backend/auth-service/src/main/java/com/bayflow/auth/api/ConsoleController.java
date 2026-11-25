package com.bayflow.auth.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.PageResponse;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.ConsoleService;
import com.bayflow.auth.domain.DemoRequest;
import com.bayflow.auth.domain.Platform;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The Ramplight console (Ramplight's own team, PLATFORM_ADMIN of the console organization): demo
 * requests now; invitations and workshops in the next steps.
 */
@RestController
@RequestMapping("/api/v1/auth/platform")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ramplight console")
public class ConsoleController {

    private static final int MAX_PAGE_SIZE = 100;

    public record DemoRequestResponse(UUID id, String firstName, String lastName, String email, String phone,
            String shopName, String shopType, String locations, String city, String message,
            DemoRequest.Status status, String notes, Instant createdAt, Instant updatedAt, String updatedBy,
            long version, int repeatCount, Instant lastRequestedAt) {

        static DemoRequestResponse from(DemoRequest d) {
            return new DemoRequestResponse(d.getId(), d.getFirstName(), d.getLastName(), d.getEmail(), d.getPhone(),
                    d.getShopName(), d.getShopType(), d.getLocations(), d.getCity(), d.getMessage(), d.getStatus(),
                    d.getNotes(), d.getCreatedAt(), d.getUpdatedAt(), d.getUpdatedBy(),
                    d.getVersion() == null ? 0 : d.getVersion(), d.getRepeatCount(), d.getLastRequestedAt());
        }
    }

    public record UpdateDemoRequest(@NotNull DemoRequest.Status status, @Size(max = 2000) String notes,
            @NotNull Long version) {
    }

    private final ConsoleService console;

    public ConsoleController(ConsoleService console) {
        this.console = console;
    }

    @GetMapping("/demo-requests")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Demo requests, newest first (status, q: name, email, phone or shop)")
    public PageResponse<DemoRequestResponse> demoRequests(@RequestParam(required = false) DemoRequest.Status status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        requireConsole();
        Page<DemoRequest> found = console.demoRequests(status, q, Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return new PageResponse<>(found.getContent().stream().map(DemoRequestResponse::from).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @GetMapping("/demo-requests/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "One demo request")
    public DemoRequestResponse demoRequest(@PathVariable UUID id) {
        requireConsole();
        return DemoRequestResponse.from(console.demoRequest(id));
    }

    @PutMapping("/demo-requests/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Move a demo request on (status) and keep notes; send the version read")
    public DemoRequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDemoRequest request,
            Authentication auth) {
        requireConsole();
        return DemoRequestResponse.from(console.update(CurrentUser.from(auth), id, request.version(),
                request.status(), request.notes()));
    }

    /** The role alone is not enough: the token must also be the console organization's. */
    static void requireConsole() {
        if (!Platform.TENANT_ID.equals(TenantContext.current().orElse(null))) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "console-only", "Forbidden",
                    "This is for the Ramplight console only.");
        }
    }
}
