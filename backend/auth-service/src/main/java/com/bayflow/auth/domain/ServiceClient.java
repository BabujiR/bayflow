package com.bayflow.auth.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A machine client (e.g. appointment-service) allowed to use the client-credentials grant. */
@Entity
@Table(name = "auth_clients")
public class ServiceClient {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "secret_hash", nullable = false, length = 100)
    private String secretHash;

    /** Comma-separated role names, e.g. "SYSTEM". */
    @Column(nullable = false, length = 200)
    private String roles;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected ServiceClient() {
        // for JPA
    }

    public ServiceClient(String clientId, String secretHash, List<String> roles, Instant now) {
        this.clientId = clientId;
        this.secretHash = secretHash;
        this.roles = String.join(",", roles);
        this.enabled = true;
        this.createdAt = now;
    }

    public void update(String secretHash, List<String> roles) {
        this.secretHash = secretHash;
        this.roles = String.join(",", roles);
        this.enabled = true;
    }

    public List<String> getRoleList() {
        return Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    public String getClientId() { return clientId; }
    public String getSecretHash() { return secretHash; }
    public boolean isEnabled() { return enabled; }
}
