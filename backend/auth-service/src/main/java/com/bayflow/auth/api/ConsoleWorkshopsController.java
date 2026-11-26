package com.bayflow.auth.api;

import static com.bayflow.auth.api.AuthDtos.USERNAME_MESSAGE;
import static com.bayflow.auth.api.AuthDtos.USERNAME_PATTERN;
import static com.bayflow.auth.api.ConsoleController.requireConsole;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.PageResponse;
import com.bayflow.auth.application.ConsoleWorkshopService;
import com.bayflow.auth.application.ConsoleWorkshopService.WorkshopRow;
import com.bayflow.auth.application.SignupService;
import com.bayflow.auth.application.SignupService.Created;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The Ramplight console: every workshop, suspend / reactivate, and test workshops. */
@RestController
@RequestMapping("/api/v1/auth/platform/workshops")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ramplight console")
public class ConsoleWorkshopsController {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWorkshopsController.class);
    private static final int MAX_PAGE_SIZE = 100;

    /** {@code invitedEmail}: the setup invite it came from (null for test and older workshops). */
    public record WorkshopResponse(UUID id, String slug, String name, Tenant.Status status, boolean test,
            Instant createdAt, int staff, int customers, int locations, String ownerUsername, String ownerEmail,
            String invitedEmail, long version) {

        static WorkshopResponse from(WorkshopRow row) {
            Tenant t = row.tenant();
            var s = row.stats();
            return new WorkshopResponse(t.getId(), t.getSlug(), t.getName(), t.getStatus(), t.isTest(),
                    t.getCreatedAt(), s == null ? 0 : s.getStaff(), s == null ? 0 : s.getCustomers(),
                    s == null ? 0 : s.getLocations(), s == null ? null : s.getOwnerUsername(),
                    s == null ? null : s.getOwnerEmail(),
                    row.invitation() == null ? null : row.invitation().getEmail(),
                    t.getVersion() == null ? 0 : t.getVersion());
        }
    }

    public record TestWorkshopRequest(
            @NotBlank @Size(max = 100) String workshopName,
            @NotBlank @Size(min = 3, max = 30) String slug,
            @Size(max = 3) String currency,
            @Size(max = 64) String timeZone,
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = USERNAME_PATTERN, message = USERNAME_MESSAGE)
            String adminUsername,
            @NotBlank @Email @Size(max = 254) String adminEmail,
            @NotBlank @Size(min = 8, max = 100) String adminPassword) {
    }

    private final ConsoleWorkshopService workshops;
    private final SignupService signups;

    public ConsoleWorkshopsController(ConsoleWorkshopService workshops, SignupService signups) {
        this.workshops = workshops;
        this.signups = signups;
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Workshops, newest first (status, test, q: name or address)")
    public PageResponse<WorkshopResponse> list(@RequestParam(required = false) Tenant.Status status,
            @RequestParam(required = false) Boolean test, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        requireConsole();
        Page<WorkshopRow> found = workshops.search(status, test, q, Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return new PageResponse<>(found.getContent().stream().map(WorkshopResponse::from).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Create a test workshop (no invite; labelled Test) with its first ADMIN")
    public WorkshopResponse createTest(@Valid @RequestBody TestWorkshopRequest request, Authentication auth) {
        requireConsole();
        UUID id = UUID.randomUUID();
        Created created = TenantContext.call(id, () -> signups.createTest(id, new SignupService.Signup(
                request.workshopName(), request.slug(), null, request.currency(), request.timeZone(),
                request.adminUsername(), request.adminEmail(), request.adminPassword(), null, null)));
        log.info("Test workshop '{}' created by {}", created.tenant().getSlug(), CurrentUser.from(auth).username());
        return new WorkshopResponse(created.tenant().getId(), created.tenant().getSlug(), created.tenant().getName(),
                created.tenant().getStatus(), true, created.tenant().getCreatedAt(), 1, 0, 1,
                created.admin().getUsername(), created.admin().getEmail(), null,
                created.tenant().getVersion() == null ? 0 : created.tenant().getVersion());
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Suspend a workshop: nobody can sign in at its address; its data is kept")
    public void suspend(@PathVariable UUID id, Authentication auth) {
        requireConsole();
        workshops.suspend(CurrentUser.from(auth).username(), id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Reactivate a suspended workshop")
    public void reactivate(@PathVariable UUID id, Authentication auth) {
        requireConsole();
        workshops.reactivate(CurrentUser.from(auth).username(), id);
    }
}
