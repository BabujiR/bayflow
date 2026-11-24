package com.bayflow.auth.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.application.DemoRequestService;
import com.bayflow.auth.domain.DemoRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** "Request a demo" on the public site (no sign-in; rate-limited per IP). */
@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Workshops")
public class DemoRequestController {

    public record DemoRequestBody(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[0-9+()\\-. ]{7,30}$", message = "Enter a phone number.") String phone,
            @NotBlank @Size(max = 120) String shopName,
            @NotBlank @Pattern(regexp = "GENERAL_REPAIR|TIRES|QUICK_LUBE|COLLISION|DEALERSHIP|FLEET|OTHER")
            String shopType,
            @NotBlank @Pattern(regexp = "1|2-3|4-10|11\\+", message = "Choose how many locations.") String locations,
            @Size(max = 120) String city,
            @Size(max = 1000) String message) {
    }

    public record DemoResponse(UUID id) {
    }

    private final DemoRequestService demos;

    public DemoRequestController(DemoRequestService demos) {
        this.demos = demos;
    }

    @PostMapping("/demo-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for a demo (public, rate-limited per IP)",
            description = "Send the X-Bayflow-Client header like the web app.")
    public DemoResponse request(@Valid @RequestBody DemoRequestBody body, HttpServletRequest httpRequest) {
        CookieRequestGuard.check(httpRequest);
        DemoRequest saved = demos.submit(new DemoRequest.Details(body.firstName(), body.lastName(), body.email(),
                body.phone(), body.shopName(), body.shopType(), body.locations(), body.city(), body.message()),
                httpRequest.getRemoteAddr());
        return new DemoResponse(saved.getId());
    }
}
