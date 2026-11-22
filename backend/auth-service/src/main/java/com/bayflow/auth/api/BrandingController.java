package com.bayflow.auth.api;

import java.io.IOException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bayflow.auth.application.ApiException;
import com.bayflow.auth.application.BrandingService;
import com.bayflow.auth.application.BrandingService.BrandingUpdate;
import com.bayflow.auth.application.BrandingService.BrandingView;
import com.bayflow.auth.domain.TenantAsset.Kind;
import com.bayflow.auth.domain.TenantBranding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The workshop's branding, edited by its ADMIN. Customers see it through /api/v1/tenants/current. */
@RestController
@RequestMapping("/api/v1/auth/tenant/branding")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Workshop branding")
public class BrandingController {

    /** Colour, "Powered by Bayflow" and the images; announcements are under /announcements. */
    public record BrandingResponse(String primaryColor, boolean showPoweredBy, String logoUrl, String coverUrl,
            String logoDarkUrl, String iconUrl, long version) {

        static BrandingResponse from(BrandingView view) {
            TenantBranding b = view.branding();
            return new BrandingResponse(b.getPrimaryColor(), b.isShowPoweredBy(),
                    imageUrl(Kind.LOGO, view.logoSha256()), imageUrl(Kind.COVER, view.coverSha256()),
                    imageUrl(Kind.LOGO_DARK, view.logoDarkSha256()), imageUrl(Kind.ICON, view.iconSha256()),
                    b.getVersion() == null ? 0 : b.getVersion());
        }
    }

    public record UpdateBrandingRequest(
            @Size(max = 7) String primaryColor,
            @NotNull Boolean showPoweredBy,
            @NotNull Long version) {
    }

    private final BrandingService branding;

    public BrandingController(BrandingService branding) {
        this.branding = branding;
    }

    @GetMapping
    @Operation(summary = "The workshop's branding (workshop ADMIN)")
    public BrandingResponse get() {
        return BrandingResponse.from(branding.current());
    }

    @PutMapping
    @Operation(summary = "Change colour and 'Powered by Ramplight' (workshop ADMIN)",
            description = "Send the version you read; 409 if someone changed the branding meanwhile. "
                    + "Empty colour = Ramplight's colours.")
    public BrandingResponse update(@Valid @RequestBody UpdateBrandingRequest request) {
        return BrandingResponse.from(branding.update(new BrandingUpdate(request.primaryColor(),
                request.showPoweredBy(), request.version())));
    }

    @PutMapping(path = "/{kind:" + KINDS + "}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the logo, dark-mode logo, icon or cover photo (workshop ADMIN)",
            description = "kind = logo or logo-dark (PNG/JPEG, up to 1 MB, kept up to 512 px), icon (PNG/JPEG, "
                    + "up to 1 MB, made square on a transparent background, up to 512 px) or cover (PNG/JPEG, "
                    + "up to 3 MB, kept up to 2000 px). The image is re-encoded; its metadata is removed.")
    public BrandingResponse upload(@PathVariable String kind, @RequestPart("file") MultipartFile file)
            throws IOException {
        return BrandingResponse.from(branding.upload(kindOf(kind), file.getBytes()));
    }

    @DeleteMapping("/{kind:" + KINDS + "}")
    @Operation(summary = "Remove the logo, dark-mode logo, icon or cover photo (workshop ADMIN)")
    public BrandingResponse remove(@PathVariable String kind) {
        return BrandingResponse.from(branding.remove(kindOf(kind)));
    }

    /** The image kinds in addresses (…/branding/logo-dark, /tenants/current/icon). */
    static final String KINDS = "logo|logo-dark|icon|cover";

    static Kind kindOf(String kind) {
        return switch (kind) {
            case "logo" -> Kind.LOGO;
            case "logo-dark" -> Kind.LOGO_DARK;
            case "icon" -> Kind.ICON;
            case "cover" -> Kind.COVER;
            default -> throw ApiException.notFound("The branding image");
        };
    }

    static String pathOf(Kind kind) {
        return switch (kind) {
            case LOGO -> "logo";
            case LOGO_DARK -> "logo-dark";
            case ICON -> "icon";
            case COVER -> "cover";
        };
    }

    /** The public, cache-busting address of an image, or null when the workshop has none. */
    static String imageUrl(Kind kind, String sha256) {
        if (sha256 == null) {
            return null;
        }
        return "/api/v1/tenants/current/" + pathOf(kind) + "?v=" + version(sha256);
    }

    static String version(String sha256) {
        return sha256.substring(0, 16);
    }
}
