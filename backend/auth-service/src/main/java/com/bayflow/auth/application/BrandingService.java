package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.auth.domain.TenantAsset;
import com.bayflow.auth.domain.TenantAsset.Kind;
import com.bayflow.auth.domain.TenantBranding;
import com.bayflow.auth.domain.TenantBranding.BannerStyle;
import com.bayflow.auth.infrastructure.TenantAssetRepository;
import com.bayflow.auth.infrastructure.TenantBrandingRepository;
import com.bayflow.security.TenantContext;

/** The current workshop's branding (in {@link TenantContext}): colour, banner, logo and cover photo. */
@Service
public class BrandingService {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    /** Control characters and line separators: the banner is a single line of plain text. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    /** Branding with the hashes of the images the workshop has (null when it has none). */
    public record BrandingView(TenantBranding branding, String logoSha256, String coverSha256) {
    }

    public record BrandingUpdate(String primaryColor, String bannerText, BannerStyle bannerStyle,
            Instant bannerStartsAt, Instant bannerEndsAt, boolean showPoweredBy, long version) {
    }

    private final TenantBrandingRepository branding;
    private final TenantAssetRepository assets;
    private final Clock clock;

    public BrandingService(TenantBrandingRepository branding, TenantAssetRepository assets, Clock clock) {
        this.branding = branding;
        this.assets = assets;
        this.clock = clock;
    }

    /** The workshop's branding; Bayflow's defaults (not saved) when it has never set any. */
    @Transactional(readOnly = true)
    public BrandingView current() {
        UUID tenantId = TenantContext.require();
        TenantBranding own = branding.findByTenantId(tenantId)
                .orElseGet(() -> TenantBranding.defaults(tenantId, clock.instant()));
        Map<Kind, String> versions = assets.findVersions().stream()
                .collect(Collectors.toMap(TenantAssetRepository.AssetVersion::getKind,
                        TenantAssetRepository.AssetVersion::getSha256));
        return new BrandingView(own, versions.get(Kind.LOGO), versions.get(Kind.COVER));
    }

    /** Changes colour, banner and "Powered by Bayflow"; {@code version} must match what the caller read. */
    @Transactional
    public BrandingView update(BrandingUpdate update) {
        String color = color(update.primaryColor());
        String banner = bannerText(update.bannerText());
        if (update.bannerStartsAt() != null && update.bannerEndsAt() != null
                && !update.bannerStartsAt().isBefore(update.bannerEndsAt())) {
            throw ApiException.badRequest("invalid-banner-window", "The banner must end after it starts.");
        }
        UUID tenantId = TenantContext.require();
        Instant now = clock.instant();
        TenantBranding own = branding.findByTenantId(tenantId).orElse(null);
        long currentVersion = own == null || own.getVersion() == null ? 0 : own.getVersion();
        if (currentVersion != update.version()) {
            throw ApiException.conflict("concurrent-update",
                    "The branding was changed by someone else. Reload and try again.");
        }
        if (own == null) {
            // Saved first (version 0) so the change below makes it version 1, like every later change.
            own = branding.saveAndFlush(TenantBranding.defaults(tenantId, now));
        }
        own.update(color, banner, update.bannerStyle(), update.bannerStartsAt(), update.bannerEndsAt(),
                update.showPoweredBy(), now);
        branding.saveAndFlush(own);
        return current();
    }

    /** Checks, re-encodes and stores a logo or cover photo, replacing the previous one. */
    @Transactional
    public BrandingView upload(Kind kind, byte[] upload) {
        BrandingImages.Processed image = BrandingImages.process(kind, upload);
        Instant now = clock.instant();
        TenantAsset asset = assets.findByKind(kind)
                .map(existing -> {
                    existing.replace(image.contentType(), image.content(), image.sha256(), now);
                    return existing;
                })
                .orElseGet(() -> TenantAsset.of(kind, image.contentType(), image.content(), image.sha256(), now));
        assets.saveAndFlush(asset);
        return current();
    }

    /** Removes a logo or cover photo (Bayflow's own is shown again). Removing a missing one is fine. */
    @Transactional
    public BrandingView remove(Kind kind) {
        assets.findByKind(kind).ifPresent(asset -> {
            assets.delete(asset);
            assets.flush();
        });
        return current();
    }

    /** The image for the public address, if the workshop has one. */
    @Transactional(readOnly = true)
    public Optional<TenantAsset> image(Kind kind) {
        return assets.findByKind(kind);
    }

    /** {@code #rrggbb} in lower case, or null for Bayflow's colours. */
    static String color(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String color = value.trim();
        if (!COLOR.matcher(color).matches()) {
            throw ApiException.badRequest("invalid-color", "Use a colour like #1d4ed8.");
        }
        return color.toLowerCase();
    }

    /** One line of plain text, or null for no banner. The web app shows it as text, never as HTML. */
    static String bannerText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = CONTROL.matcher(value).replaceAll(" ").trim().replaceAll(" {2,}", " ");
        if (text.length() > 200) {
            throw ApiException.badRequest("invalid-banner", "The banner may be at most 200 characters.");
        }
        return text.isEmpty() ? null : text;
    }
}
