package com.bayflow.workshop.cannedjobs;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.security.TenantContext;
import com.bayflow.workshop.application.ApiException;
import com.bayflow.workshop.cannedjobs.CannedJob.Details;
import com.bayflow.workshop.integration.WorkshopSettingsClient;

/**
 * Canned jobs: managers and admins keep the list (role checks on the API; row-level security
 * keeps managers to their locations' jobs and the organization-wide ones); staff pick from it.
 */
@Service
public class CannedJobService {

    private static final Logger log = LoggerFactory.getLogger(CannedJobService.class);
    /** A shop's list is short; this keeps a runaway one from filling a page. */
    static final int MAX_RESULTS = 500;

    private final CannedJobRepository jobs;
    private final WorkshopSettingsClient settings;
    private final Clock clock;

    public CannedJobService(CannedJobRepository jobs, WorkshopSettingsClient settings, Clock clock) {
        this.jobs = jobs;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Jobs by category and name. locationId: only those usable there (its own and the
     * organization-wide ones); q: in the name, category or description.
     */
    @Transactional(readOnly = true)
    public List<CannedJob> list(UUID locationId, String query, boolean includeInactive) {
        Specification<CannedJob> spec = (root, q, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (!includeInactive) {
                where.add(cb.isTrue(root.get("active")));
            }
            if (locationId != null) {
                where.add(cb.or(cb.isNull(root.get("locationId")), cb.equal(root.get("locationId"), locationId)));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.strip().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("category"), "")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        List<CannedJob> found = jobs.findAll(spec, Sort.by(Sort.Order.asc("category"),
                Sort.Order.asc("name"))).stream().limit(MAX_RESULTS).toList();
        found.forEach(CannedJob::loadDetails);
        return found;
    }

    @Transactional(readOnly = true)
    public CannedJob get(UUID id) {
        CannedJob job = find(id);
        job.loadDetails();
        return job;
    }

    @Transactional
    public CannedJob create(CurrentUser user, Details details) {
        checkLocation(details.locationId());
        CannedJob job = jobs.save(CannedJob.create(details, user.username(), clock.instant()));
        log.info("Canned job '{}' created by {}", job.getName(), user.username());
        job.loadDetails();
        return job;
    }

    /** {@code version}: the one read (409 when someone changed it meanwhile). */
    @Transactional
    public CannedJob update(UUID id, long version, Details details) {
        CannedJob job = find(id);
        if (job.getVersion() != null && job.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "This canned job was changed by someone else. Reload and try again.");
        }
        checkLocation(details.locationId());
        job.update(details, clock.instant());
        CannedJob saved = jobs.saveAndFlush(job);
        saved.loadDetails();
        return saved;
    }

    /** Archived jobs are no longer offered; recommendations made from them keep their text. */
    @Transactional
    public CannedJob setActive(UUID id, boolean active) {
        CannedJob job = find(id);
        job.setActive(active, clock.instant());
        CannedJob saved = jobs.saveAndFlush(job);
        saved.loadDetails();
        return saved;
    }

    /** For a job card: the canned job, active and usable at the card's location (400 otherwise). */
    @Transactional(readOnly = true)
    public CannedJob forJobCard(UUID id, UUID locationId) {
        CannedJob job = jobs.findById(id).filter(CannedJob::isActive).filter(j -> j.appliesTo(locationId))
                .orElseThrow(() -> ApiException.badRequest("unknown-canned-job",
                        "That canned job doesn't exist, is archived or is for another location."));
        job.loadDetails();
        return job;
    }

    private CannedJob find(UUID id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Canned job"));
    }

    private void checkLocation(UUID locationId) {
        if (locationId == null) {
            return;
        }
        try {
            settings.get(TenantContext.require(), locationId);
        } catch (WorkshopSettingsClient.UnknownLocationException e) {
            throw ApiException.badRequest("unknown-location", "That location is not one of the organization's.");
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "settings-unavailable", "Service Unavailable",
                    "The locations could not be checked. Try again in a moment.");
        }
    }
}
