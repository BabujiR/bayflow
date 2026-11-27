package com.bayflow.workshop.estimates;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.estimates.EstimateLinks.EstimateLinkStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Staff: the customer's approval link of a job card's estimate. */
@RestController
@RequestMapping("/api/v1/job-cards/{id}/estimate-link")
@PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Job cards")
public class EstimateLinkController {

    /** {@code to}: another address than the customer's. */
    public record SendEstimateLinkRequest(@Email @Size(max = 254) String to) {
    }

    private final EstimateLinks links;

    public EstimateLinkController(EstimateLinks links) {
        this.links = links;
    }

    @GetMapping
    @Operation(summary = "The approval link e-mailed to the customer: sent, viewed, reminded, signed (204: none)")
    public ResponseEntity<EstimateLinkStatus> status(@PathVariable UUID id, Authentication auth) {
        return links.status(CurrentUser.from(auth), id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @Operation(summary = "E-mail the customer a link to approve or decline and sign (replaces an earlier link)",
            description = "Only while the estimate awaits approval. 400 no-email-address when the customer has "
                    + "none and no 'to' is given; 503 when e-mail is not set up.")
    public EstimateLinkStatus send(@PathVariable UUID id,
            @Valid @RequestBody(required = false) SendEstimateLinkRequest body, Authentication auth) {
        return links.send(CurrentUser.from(auth), id, body == null ? null : body.to());
    }

    @GetMapping(path = "/signature", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "The customer's signature, once they signed the estimate online (404 before)")
    public ResponseEntity<byte[]> signature(@PathVariable UUID id, Authentication auth) {
        return links.signature(CurrentUser.from(auth), id)
                .map(png -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
