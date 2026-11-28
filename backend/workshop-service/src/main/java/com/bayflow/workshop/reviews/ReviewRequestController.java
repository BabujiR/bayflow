package com.bayflow.workshop.reviews;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.reviews.ReviewRequests.ReviewRequestStatus;
import com.bayflow.workshop.reviews.ReviewRequests.Stopped;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Google review requests: their state on a paid invoice, and the customer's stop link. */
@RestController
@Tag(name = "Review requests")
public class ReviewRequestController {

    /** The stop link's token (it goes in the body, out of server logs). */
    public record StopRequest(@NotBlank @Size(max = 64) String token) {
    }

    private final ReviewRequests reviews;

    public ReviewRequestController(ReviewRequests reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/v1/invoices/{id}/review-request")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "The invoice's Google review request: NONE, SCHEDULED, SENT, SKIPPED (with why) or CANCELLED")
    public ReviewRequestStatus status(@PathVariable UUID id) {
        return reviews.status(id);
    }

    @PostMapping("/api/v1/invoices/{id}/review-request/send")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Ask for a Google review now (paid invoices; needs the location's review link)",
            description = "409 invoice-not-paid, review-already-requested, review-opted-out or no-review-link; "
                    + "400 no-email-address.")
    public ReviewRequestStatus send(@PathVariable UUID id, Authentication auth) {
        return reviews.sendNow(CurrentUser.from(auth), id);
    }

    @PostMapping("/api/v1/invoices/{id}/review-request/cancel")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Don't send the scheduled review request (409 review-not-scheduled)")
    public ReviewRequestStatus cancel(@PathVariable UUID id, Authentication auth) {
        return reviews.cancel(CurrentUser.from(auth), id);
    }

    @PostMapping("/api/v1/review-requests/stop")
    @Operation(summary = "Public: the customer's stop link; no more review requests from this workshop",
            description = "404 review-link-invalid for an unknown token. Repeats are harmless.")
    public Stopped stop(@Valid @RequestBody StopRequest request) {
        return reviews.stop(request.token());
    }
}
