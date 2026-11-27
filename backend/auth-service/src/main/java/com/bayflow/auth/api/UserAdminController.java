package com.bayflow.auth.api;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.auth.api.AuthDtos.CreateStaffRequest;
import com.bayflow.auth.api.AuthDtos.PageResponse;
import com.bayflow.auth.api.AuthDtos.UpdateLocationsRequest;
import com.bayflow.auth.api.AuthDtos.UpdateRolesRequest;
import com.bayflow.auth.api.AuthDtos.UpdateUserRequest;
import com.bayflow.auth.api.AuthDtos.UserResponse;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.LocationAccess;
import com.bayflow.auth.application.UserAdminService;
import com.bayflow.auth.domain.User;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth/users")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User accounts (ADMIN)")
public class UserAdminController {

    /** Fields the account list can be sorted by. */
    static final Set<String> SORTABLE = Set.of("username", "email", "createdAt", "enabled");

    private final UserAdminService userAdmin;

    public UserAdminController(UserAdminService userAdmin) {
        this.userAdmin = userAdmin;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a MANAGER, SERVICE_ADVISOR, MECHANIC or ADMIN account",
            description = "MANAGER, SERVICE_ADVISOR and MECHANIC work at locationIds; without them, at the "
                    + "organization's only location (400 locations-required if it has several).")
    public UserResponse create(@Valid @RequestBody CreateStaffRequest request) {
        User user = userAdmin.createStaff(request.username(), request.email(), request.password(), request.roleSet(),
                request.locationIds());
        return UserResponse.from(user, userAdmin.accessOf(user));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Set everything a staff member does (one or more of ADMIN, MANAGER, SERVICE_ADVISOR, "
            + "MECHANIC)",
            description = "Applies from the user's next token (within 15 minutes). 409 last-admin / "
                    + "cannot-remove-own-admin keep the workshop administrable.")
    public UserResponse setRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request,
            Authentication auth) {
        User user = userAdmin.setRoles(id, EnumSet.copyOf(request.roles()), request.locationIds(),
                UUID.fromString(CurrentUser.from(auth).subject()));
        return UserResponse.from(user, userAdmin.accessOf(user));
    }

    @PutMapping("/{id}/locations")
    @Operation(summary = "Set the locations a MANAGER, SERVICE_ADVISOR or MECHANIC works at",
            description = "Applies from the user's next token (within 15 minutes).")
    public UserResponse setLocations(@PathVariable UUID id, @Valid @RequestBody UpdateLocationsRequest request) {
        User user = userAdmin.setLocations(id, request.locationIds());
        return UserResponse.from(user, userAdmin.accessOf(user));
    }

    @GetMapping
    @Operation(summary = "List accounts: the team (kind=staff), customers (kind=customer) or all (default)",
            description = "Sort by username, email, createdAt or enabled, e.g. sort=createdAt,desc")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "all") String kind,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw ApiException.badRequest("invalid-sort",
                        "Cannot sort by '" + order.getProperty() + "'. Use one of: " + String.join(", ",
                                SORTABLE.stream().sorted().toList()) + ".");
            }
        }
        UserAdminService.Kind which = switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "staff" -> UserAdminService.Kind.STAFF;
            case "customer" -> UserAdminService.Kind.CUSTOMER;
            case "all" -> UserAdminService.Kind.ALL;
            default -> throw ApiException.badRequest("invalid-kind", "Use kind=staff, customer or all.");
        };
        Page<User> page = userAdmin.list(which, pageable);
        Map<UUID, LocationAccess> access = userAdmin.accessOf(page.getContent());
        return PageResponse.from(page, u -> UserResponse.from(u, access.get(u.getId())));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Enable or disable an account; disabling ends its sessions")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        UUID actingUserId = UUID.fromString(CurrentUser.from(authentication).subject());
        User user = userAdmin.setEnabled(id, request.enabled(), actingUserId);
        return UserResponse.from(user, userAdmin.accessOf(user));
    }
}
