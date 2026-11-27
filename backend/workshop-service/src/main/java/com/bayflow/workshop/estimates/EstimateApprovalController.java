package com.bayflow.workshop.estimates;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.estimates.EstimateLinks.CustomerEstimate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The customer's estimate page, opened from the e-mailed link: nobody is signed in, the token is
 * the key. POST keeps the token out of URLs in logs.
 */
@RestController
@RequestMapping("/api/v1/estimate-approval")
@Tag(name = "Estimate approval (public, by link)")
public class EstimateApprovalController {

    public record EstimateTokenRequest(@NotBlank @Size(max = 64) String token) {
    }

    public record ItemDecision(@NotNull UUID itemId, @NotNull Boolean approve) {
    }

    /** {@code signature}: the drawn signature as a PNG data URL. */
    public record EstimateDecisionRequest(@NotBlank @Size(max = 64) String token,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull ItemDecision> decisions,
            @NotBlank @Size(max = 80) String signerName, @NotBlank @Size(max = 300_000) String signature) {
    }

    private final EstimateLinks links;

    public EstimateApprovalController(EstimateLinks links) {
        this.links = links;
    }

    @PostMapping("/view")
    @Operation(summary = "The estimate a link names (404 unknown, 410 replaced or expired)")
    public CustomerEstimate view(@Valid @RequestBody EstimateTokenRequest body) {
        return links.view(body.token());
    }

    @PostMapping("/decide")
    @Operation(summary = "Approve or decline every pending item and sign (409 estimate-decided when already answered)")
    public CustomerEstimate decide(@Valid @RequestBody EstimateDecisionRequest body, HttpServletRequest request) {
        Map<UUID, Boolean> decisions = new LinkedHashMap<>();
        for (ItemDecision d : body.decisions()) {
            if (decisions.put(d.itemId(), d.approve()) != null) {
                throw ApiException.badRequest("decide-every-item", "Each item can be answered once.");
            }
        }
        return links.decide(body.token(), decisions, body.signerName(), body.signature(), clientIp(request),
                request.getHeader("User-Agent"));
    }

    /** The caller's address as nginx saw it (X-Real-IP), else the connection's. */
    private static String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real.strip() : request.getRemoteAddr();
    }
}
