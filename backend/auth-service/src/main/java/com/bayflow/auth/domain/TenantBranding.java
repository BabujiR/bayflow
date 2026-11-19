package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/** How a workshop presents itself to its customers: colour, banner, "Powered by Bayflow". */
@Entity
@Table(name = "auth_tenant_branding")
public class TenantBranding {

    public enum BannerStyle { INFO, IMPORTANT }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** #rrggbb, or null for Bayflow's colours. */
    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "banner_text", length = 200)
    private String bannerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "banner_style", nullable = false, length = 20)
    private BannerStyle bannerStyle = BannerStyle.INFO;

    @Column(name = "banner_starts_at")
    private Instant bannerStartsAt;

    @Column(name = "banner_ends_at")
    private Instant bannerEndsAt;

    @Column(name = "show_powered_by", nullable = false)
    private boolean showPoweredBy = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected TenantBranding() {
        // for JPA
    }

    /** Bayflow's look: no colour, no banner, "Powered by Bayflow" shown. */
    public static TenantBranding defaults(UUID tenantId, Instant now) {
        TenantBranding branding = new TenantBranding();
        branding.id = UUID.randomUUID();
        branding.tenantId = tenantId;
        branding.updatedAt = now;
        return branding;
    }

    public void update(String primaryColor, String bannerText, BannerStyle bannerStyle, Instant bannerStartsAt,
            Instant bannerEndsAt, boolean showPoweredBy, Instant now) {
        this.primaryColor = primaryColor;
        this.bannerText = bannerText;
        this.bannerStyle = bannerStyle == null ? BannerStyle.INFO : bannerStyle;
        this.bannerStartsAt = bannerStartsAt;
        this.bannerEndsAt = bannerEndsAt;
        this.showPoweredBy = showPoweredBy;
        this.updatedAt = now;
    }

    /** True when the banner has text and {@code now} is within its dates (open-ended if unset). */
    public boolean isBannerLive(Instant now) {
        return bannerText != null && !bannerText.isBlank()
                && (bannerStartsAt == null || !now.isBefore(bannerStartsAt))
                && (bannerEndsAt == null || now.isBefore(bannerEndsAt));
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPrimaryColor() { return primaryColor; }
    public String getBannerText() { return bannerText; }
    public BannerStyle getBannerStyle() { return bannerStyle; }
    public Instant getBannerStartsAt() { return bannerStartsAt; }
    public Instant getBannerEndsAt() { return bannerEndsAt; }
    public boolean isShowPoweredBy() { return showPoweredBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
