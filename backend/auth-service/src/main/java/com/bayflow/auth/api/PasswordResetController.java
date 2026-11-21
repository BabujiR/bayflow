package com.bayflow.auth.api;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.TemporaryPasswordResponse;
import com.bayflow.auth.application.UserAdminService;
import com.bayflow.auth.application.UserAdminService.TemporaryPassword;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Staff reset a forgotten password; the person chooses their own at the next sign-in. */
@RestController
@RequestMapping("/api/v1/auth/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User accounts (ADMIN)")
public class PasswordResetController {

    private final UserAdminService userAdmin;

    public PasswordResetController(UserAdminService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Reset a forgotten password (ADMIN: anyone else; MANAGER, SERVICE_ADVISOR: customers)",
            description = "Returns a temporary password, shown once. It must be replaced at the next sign-in, "
                    + "stops working after bayflow.auth.temporary-password-ttl (72 hours), and the account is "
                    + "unlocked and signed out everywhere.")
    public ResponseEntity<TemporaryPasswordResponse> reset(@PathVariable UUID id, Authentication authentication) {
        TemporaryPassword reset = userAdmin.resetPassword(id, CurrentUser.from(authentication));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TemporaryPasswordResponse(reset.user().getId(), reset.user().getUsername(),
                        reset.password(), reset.expiresAt()));
    }
}
