package com.bayflow.auth.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.CreateStaffRequest;
import com.bayflow.auth.api.AuthDtos.PageResponse;
import com.bayflow.auth.api.AuthDtos.UpdateUserRequest;
import com.bayflow.auth.api.AuthDtos.UserResponse;
import com.bayflow.auth.application.UserAdminService;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth/users")
@PreAuthorize("hasRole('SERVICE_ADVISOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Staff accounts")
public class UserAdminController {

    private final UserAdminService userAdmin;

    public UserAdminController(UserAdminService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a SERVICE_ADVISOR or MECHANIC account")
    public UserResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return UserResponse.from(
                userAdmin.createStaff(request.username(), request.email(), request.password(), request.role()));
    }

    @GetMapping
    @Operation(summary = "List all accounts")
    public PageResponse<UserResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(userAdmin.list(pageable), UserResponse::from);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Enable or disable an account; disabling ends its sessions")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        UUID actingUserId = UUID.fromString(CurrentUser.from(authentication).subject());
        return UserResponse.from(userAdmin.setEnabled(id, request.enabled(), actingUserId));
    }
}
