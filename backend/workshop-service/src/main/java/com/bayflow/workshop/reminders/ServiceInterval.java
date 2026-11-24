package com.bayflow.workshop.reminders;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.TenantId;

/**
 * The shop's own rule for a recurring service: every {@code miles} or every {@code months},
 * whichever comes first. Finished work counts as this service when it came from one of its
 * canned jobs or its description contains one of its keywords.
 */
@Entity
@Table(name = "ws_service_intervals")
public class ServiceInterval {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String name;

    private Integer miles;

    private Integer months;

    @Column(length = 500)
    private String keywords;

    @Column(name = "canned_job_ids", length = 2000)
    private String cannedJobIds;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** What an admin edits. */
    public record Details(String name, Integer miles, Integer months, List<String> keywords, List<UUID> cannedJobIds,
            boolean active) {
    }

    protected ServiceInterval() {
        // for JPA
    }

    public static ServiceInterval create(Details details, int position, Instant now) {
        ServiceInterval interval = new ServiceInterval();
        interval.id = UUID.randomUUID();
        interval.position = position;
        interval.createdAt = now;
        interval.update(details, now);
        return interval;
    }

    public void update(Details details, Instant now) {
        this.name = details.name().strip();
        this.miles = details.miles();
        this.months = details.months();
        Set<String> words = new LinkedHashSet<>();
        for (String word : details.keywords() == null ? List.<String>of() : details.keywords()) {
            String clean = word == null ? "" : word.strip().toLowerCase(Locale.ROOT).replace(",", " ");
            if (!clean.isEmpty()) {
                words.add(clean);
            }
        }
        this.keywords = words.isEmpty() ? null : String.join(",", words);
        Set<UUID> jobs = new LinkedHashSet<>(details.cannedJobIds() == null ? List.of() : details.cannedJobIds());
        this.cannedJobIds = jobs.isEmpty() ? null
                : jobs.stream().map(UUID::toString).collect(Collectors.joining(","));
        this.active = details.active();
        this.updatedAt = now;
    }

    /** Whether finished work counts as this service. */
    public boolean matches(UUID cannedJobId, String description) {
        if (cannedJobId != null && getCannedJobIds().contains(cannedJobId)) {
            return true;
        }
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        return getKeywords().stream().anyMatch(text::contains);
    }

    public List<String> getKeywords() {
        return keywords == null ? List.of() : Arrays.asList(keywords.split(","));
    }

    public List<UUID> getCannedJobIds() {
        return cannedJobIds == null ? List.of() : Arrays.stream(cannedJobIds.split(",")).map(UUID::fromString).toList();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public Integer getMiles() { return miles; }
    public Integer getMonths() { return months; }
    public int getPosition() { return position; }
    public boolean isActive() { return active; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
