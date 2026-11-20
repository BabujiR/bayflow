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
            long version) {

        static BrandingResponse from(BrandingView view) {
            TenantBranding b = view.branding();
            return new BrandingResponse(b.getPrimaryColor(), b.isShowPoweredBy(),
                    imageUrl(Kind.LOGO, view.logoSha256()), imageUrl(Kind.COVER, view.coverSha256()),
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
    @Operation(summary = "Change colour and 'Powered by Bayflow' (workshop ADMIN)",
            description = "Send the version you read; 409 if someone changed the branding meanwhile. "
                    + "Empty colour = Bayflow's colours.")
    public BrandingResponse update(@Valid @RequestBody UpdateBrandingRequest request) {
        return BrandingResponse.from(branding.update(new BrandingUpdate(request.primaryColor(),
                request.showPoweredBy(), request.version())));
    }

    @PutMapping(path = "/{kind:logo|cover}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the logo or cover photo (workshop ADMIN)",
            description = "kind = logo (PNG/JPEG, up to 1 MB, shown up to 512 px) or cover (PNG/JPEG, up to "
                    + "3 MB, shown up to 2000 px wide). The image is re-encoded; its metadata is removed.")
    public BrandingResponse upload(@PathVariable String kind, @RequestPart("file") MultipartFile file)
            throws IOException {
        return BrandingResponse.from(branding.upload(kindOf(kind), file.getBytes()));
    }

    @DeleteMapping("/{kind:logo|cover}")
    @Operation(summary = "Remove the logo or cover photo (workshop ADMIN)")
    public BrandingResponse remove(@PathVariable String kind) {
        return BrandingResponse.from(branding.remove(kindOf(kind)));
    }

    static Kind kindOf(String kind) {
        return switch (kind) {
            case "logo" -> Kind.LOGO;
            case "cover" -> Kind.COVER;
            default -> throw ApiException.notFound("The branding image");
        };
    }

    /** The public, cache-busting address of an image, or null when the workshop has none. */
    static String imageUrl(Kind kind, String sha256) {
        if (sha256 == null) {
            return null;
        }
        return "/api/v1/tenants/current/" + (kind == Kind.LOGO ? "logo" : "cover") + "?v=" + version(sha256);
    }

    static String version(String sha256) {
        return sha256.substring(0, 16);
    }
}
