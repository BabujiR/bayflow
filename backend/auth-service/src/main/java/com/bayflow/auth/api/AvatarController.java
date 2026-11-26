package com.bayflow.auth.api;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bayflow.auth.api.AuthDtos.AvatarRequest;
import com.bayflow.auth.api.AuthDtos.UserResponse;
import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.AuthService;
import com.bayflow.auth.application.AvatarService;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.domain.UserAvatar;
import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Avatars: each signed-in user chooses their own (initials, a built-in character or a photo); a
 * workshop admin can put anyone back to initials. Photos are served at the workshop's address
 * with an unguessable version, so an {@code <img>} can show them without a token.
 */
@RestController
@Tag(name = "Avatars")
public class AvatarController {

    private final AvatarService avatars;
    private final AuthService auth;
    private final RequestTenants tenants;

    public AvatarController(AvatarService avatars, AuthService auth, RequestTenants tenants) {
        this.avatars = avatars;
        this.auth = auth;
        this.tenants = tenants;
    }

    @PutMapping("/api/v1/auth/me/avatar")
    @Operation(summary = "Show me as my initials or a built-in character (removes my photo)")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse choose(@Valid @RequestBody AvatarRequest request, Authentication authentication) {
        UUID me = userIdOf(authentication);
        User user = switch (request.kind()) {
            case INITIALS -> avatars.useInitials(me);
            case CHARACTER -> avatars.useCharacter(me, request.character());
            case PHOTO -> throw ApiException.badRequest("upload-photo", "Upload the photo instead.");
        };
        return UserResponse.from(user, auth.accessOf(user));
    }

    @PutMapping(path = "/api/v1/auth/me/avatar/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Use my own photo (PNG or JPEG, up to 2 MB)",
            description = "Cut to its centre square, kept at 256 px as JPEG; its metadata (e.g. location) is removed.")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse upload(@RequestPart("file") MultipartFile file, Authentication authentication)
            throws IOException {
        User user = avatars.usePhoto(userIdOf(authentication), file.getBytes());
        return UserResponse.from(user, auth.accessOf(user));
    }

    @DeleteMapping("/api/v1/auth/users/{id}/avatar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Put a user back to their initials (workshop ADMIN), e.g. for an unsuitable photo")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse reset(@PathVariable UUID id) {
        User user = avatars.useInitials(id);
        return UserResponse.from(user, auth.accessOf(user));
    }

    @GetMapping("/api/v1/tenants/current/avatars/{userId}")
    @Operation(summary = "A user's photo at this workshop's address (404 unless ?v= is its current version)",
            description = "Cached by the browser for good: a new photo has a new address.")
    public ResponseEntity<byte[]> photo(@PathVariable UUID userId, @RequestParam(required = false) String v,
            HttpServletRequest request) {
        Tenant tenant = tenants.workshopOf(request);
        UserAvatar photo = TenantContext.call(tenant.getId(), () -> avatars.photo(userId, v))
                .orElseThrow(() -> ApiException.notFound("The photo"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .eTag("\"" + photo.getSha256() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getContent());
    }

    private static UUID userIdOf(Authentication authentication) {
        try {
            return UUID.fromString(CurrentUser.from(authentication).subject());
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("User");
        }
    }
}
