package com.bayflow.auth.api;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.AuditEventResponse;
import com.bayflow.auth.api.AuthDtos.PageResponse;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.AuditLog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User accounts (ADMIN)")
public class AuditController {

    static final int MAX_SIZE = 100;

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/auth/audit-events")
    @Operation(summary = "The workshop's account changes, newest first (one account's with userId)",
            description = "Staff created, roles and locations changed, accounts enabled, disabled or "
                    + "unlocked, passwords reset, photos removed: who did it, and when.")
    public PageResponse<AuditEventResponse> list(@RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw ApiException.badRequest("invalid-page", "Use page >= 0 and size 1 to " + MAX_SIZE + ".");
        }
        return PageResponse.from(audit.list(userId, PageRequest.of(page, size)), AuditEventResponse::from);
    }
}
