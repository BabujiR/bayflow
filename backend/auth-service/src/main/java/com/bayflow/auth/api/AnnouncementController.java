package com.bayflow.auth.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.AnnouncementService;
import com.bayflow.auth.application.AnnouncementService.Draft;
import com.bayflow.auth.domain.Announcement;
import com.bayflow.auth.domain.Announcement.State;
import com.bayflow.auth.domain.Announcement.Style;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The workshop's announcements, kept by its ADMIN: up to 10, each with optional dates. Everyone
 * at the workshop's address sees the live ones through /api/v1/tenants/current.
 */
@RestController
@RequestMapping("/api/v1/auth/tenant/branding/announcements")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Workshop branding")
public class AnnouncementController {

    public record AnnouncementResponse(UUID id, String text, Style style, Instant startsAt, Instant endsAt,
            State state, Instant createdAt, long version) {

        static AnnouncementResponse from(Announcement a, Instant now) {
            return new AnnouncementResponse(a.getId(), a.getText(), a.getStyle(), a.getStartsAt(), a.getEndsAt(),
                    a.stateAt(now), a.getCreatedAt(), a.getVersion() == null ? 0 : a.getVersion());
        }
    }

    /** Text up to 200 characters once tidied; longer input is refused by the service with a clear message. */
    public record AnnouncementRequest(
            @Size(max = 400) String text,
            Style style,
            Instant startsAt,
            Instant endsAt) {
    }

    public record UpdateAnnouncementRequest(
            @Size(max = 400) String text,
            Style style,
            Instant startsAt,
            Instant endsAt,
            @NotNull Long version) {
    }

    private final AnnouncementService announcements;
    private final Clock clock;

    public AnnouncementController(AnnouncementService announcements, Clock clock) {
        this.announcements = announcements;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "All the workshop's announcements, oldest first, with whether each is live, "
            + "scheduled or ended (workshop ADMIN)")
    public List<AnnouncementResponse> list() {
        Instant now = clock.instant();
        return announcements.all().stream().map(a -> AnnouncementResponse.from(a, now)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an announcement (workshop ADMIN; at most 10, 409 too-many-announcements)")
    public AnnouncementResponse create(@Valid @RequestBody AnnouncementRequest request) {
        return AnnouncementResponse.from(announcements.create(new Draft(request.text(), request.style(),
                request.startsAt(), request.endsAt())), clock.instant());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change an announcement (workshop ADMIN)",
            description = "Send the version you read; 409 if someone changed it meanwhile.")
    public AnnouncementResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return AnnouncementResponse.from(announcements.update(id, new Draft(request.text(), request.style(),
                request.startsAt(), request.endsAt()), request.version()), clock.instant());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an announcement (workshop ADMIN)")
    public void delete(@PathVariable UUID id) {
        announcements.delete(id);
    }
}
