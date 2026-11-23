package com.bayflow.workshop.quickbooks;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * An organization's link to its QuickBooks Online company: the company (realm), the OAuth tokens
 * (encrypted; see {@link TokenCipher}) and which products/services and account Ramplight books to.
 */
@Entity
@Table(name = "ws_qbo_connections")
public class QboConnection {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "realm_id", nullable = false, length = 32)
    private String realmId;

    @Column(nullable = false, length = 12)
    private String environment;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 8)
    private String country;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "refresh_expires_at")
    private Instant refreshExpiresAt;

    @Column(name = "parts_item_id", length = 32)
    private String partsItemId;

    @Column(name = "parts_item_name", length = 200)
    private String partsItemName;

    @Column(name = "labour_item_id", length = 32)
    private String labourItemId;

    @Column(name = "labour_item_name", length = 200)
    private String labourItemName;

    @Column(name = "fees_item_id", length = 32)
    private String feesItemId;

    @Column(name = "fees_item_name", length = 200)
    private String feesItemName;

    @Column(name = "deposit_account_id", length = 32)
    private String depositAccountId;

    @Column(name = "deposit_account_name", length = 200)
    private String depositAccountName;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "connected_by", nullable = false, length = 100)
    private String connectedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected QboConnection() {
        // for JPA
    }

    public static QboConnection create(String realmId, String environment, String by, Instant now) {
        QboConnection connection = new QboConnection();
        connection.id = UUID.randomUUID();
        connection.realmId = realmId;
        connection.environment = environment;
        connection.connectedAt = now;
        connection.connectedBy = by;
        connection.updatedAt = now;
        return connection;
    }

    /** Connected again (to the same or another company): new tokens; the mapping goes if the company changed. */
    public void reconnect(String realmId, String environment, String by, Instant now) {
        if (!realmId.equals(this.realmId)) {
            map(null, null, null, null, null, null, null, null);
        }
        this.realmId = realmId;
        this.environment = environment;
        this.connectedAt = now;
        this.connectedBy = by;
        this.updatedAt = now;
    }

    /** Encrypted tokens and when each expires. */
    public void tokens(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt,
            Instant now) {
        this.accessToken = accessToken;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshToken = refreshToken;
        this.refreshExpiresAt = refreshExpiresAt;
        this.updatedAt = now;
    }

    public void company(String name, String country) {
        this.companyName = name;
        this.country = country;
    }

    public void map(String partsId, String partsName, String labourId, String labourName, String feesId,
            String feesName, String depositId, String depositName) {
        this.partsItemId = partsId;
        this.partsItemName = partsName;
        this.labourItemId = labourId;
        this.labourItemName = labourName;
        this.feesItemId = feesId;
        this.feesItemName = feesName;
        this.depositAccountId = depositId;
        this.depositAccountName = depositName;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    /** Parts, labour and fees each have a product/service: invoices can be sent. */
    public boolean isMapped() {
        return partsItemId != null && labourItemId != null && feesItemId != null;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRealmId() { return realmId; }
    public String getEnvironment() { return environment; }
    public String getCompanyName() { return companyName; }
    public String getCountry() { return country; }
    public String getAccessToken() { return accessToken; }
    public Instant getAccessExpiresAt() { return accessExpiresAt; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
    public String getPartsItemId() { return partsItemId; }
    public String getPartsItemName() { return partsItemName; }
    public String getLabourItemId() { return labourItemId; }
    public String getLabourItemName() { return labourItemName; }
    public String getFeesItemId() { return feesItemId; }
    public String getFeesItemName() { return feesItemName; }
    public String getDepositAccountId() { return depositAccountId; }
    public String getDepositAccountName() { return depositAccountName; }
    public Instant getConnectedAt() { return connectedAt; }
    public String getConnectedBy() { return connectedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
