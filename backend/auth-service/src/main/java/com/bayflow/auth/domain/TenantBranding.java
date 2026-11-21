package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/** How a workshop presents itself to its customers: colour and "Powered by Bayflow" (announcements: {@link Announcement}). */
@Entity
@Table(name = "auth_tenant_branding")
public class TenantBranding {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** #rrggbb, or null for Bayflow's colours. */
    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "show_powered_by", nullable = false)
    private boolean showPoweredBy = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected TenantBranding() {
        // for JPA
    }

    /** Bayflow's look: no colour, "Powered by Bayflow" shown. */
    public static TenantBranding defaults(UUID tenantId, Instant now) {
        TenantBranding branding = new TenantBranding();
        branding.id = UUID.randomUUID();
        branding.tenantId = tenantId;
        branding.updatedAt = now;
        return branding;
    }

    public void update(String primaryColor, boolean showPoweredBy, Instant now) {
        this.primaryColor = primaryColor;
        this.showPoweredBy = showPoweredBy;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPrimaryColor() { return primaryColor; }
    public boolean isShowPoweredBy() { return showPoweredBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
