package com.bayflow.auth.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.Invitation;
import com.bayflow.auth.domain.Platform;
import com.bayflow.auth.domain.Tenant;
import com.bayflow.auth.infrastructure.InvitationRepository;
import com.bayflow.auth.infrastructure.TenantRepository;
import com.bayflow.auth.infrastructure.TenantRepository.WorkshopStats;

/** The Ramplight console's Workshops page: every workshop, suspend and reactivate. */
@Service
public class ConsoleWorkshopService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWorkshopService.class);

    /** A workshop with its numbers and, when it came from one, its setup invite. */
    public record WorkshopRow(Tenant tenant, WorkshopStats stats, Invitation invitation) {
    }

    private final TenantRepository tenants;
    private final InvitationRepository invitations;
    private final Clock clock;

    public ConsoleWorkshopService(TenantRepository tenants, InvitationRepository invitations, Clock clock) {
        this.tenants = tenants;
        this.invitations = invitations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<WorkshopRow> search(Tenant.Status status, Boolean test, String query, int page, int size) {
        String like = query == null || query.isBlank() ? null
                : "%" + query.strip().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        Page<Tenant> found = tenants.search(Platform.TENANT_ID, status, test, like, PageRequest.of(page, size));
        if (found.isEmpty()) {
            return found.map(t -> new WorkshopRow(t, null, null));
        }
        Map<UUID, WorkshopStats> stats = tenants.stats().stream()
                .collect(Collectors.toMap(WorkshopStats::getTenantId, Function.identity()));
        List<UUID> ids = found.getContent().stream().map(Tenant::getId).toList();
        Map<UUID, Invitation> invites = invitations.findByWorkshopIdIn(ids).stream()
                .collect(Collectors.toMap(Invitation::getWorkshopId, Function.identity(), (a, b) -> a));
        return found.map(t -> new WorkshopRow(t, stats.get(t.getId()), invites.get(t.getId())));
    }

    /** Nobody can sign in at its address until it is reactivated; its data is kept. */
    @Transactional
    public Tenant suspend(String by, UUID id) {
        Tenant tenant = find(id);
        tenant.suspend(clock.instant());
        log.info("Workshop '{}' suspended by {}", tenant.getSlug(), by);
        return tenants.saveAndFlush(tenant);
    }

    @Transactional
    public Tenant reactivate(String by, UUID id) {
        Tenant tenant = find(id);
        tenant.reactivate(clock.instant());
        log.info("Workshop '{}' reactivated by {}", tenant.getSlug(), by);
        return tenants.saveAndFlush(tenant);
    }

    private Tenant find(UUID id) {
        if (Platform.TENANT_ID.equals(id)) {
            throw ApiException.notFound("Workshop");
        }
        return tenants.findById(id).orElseThrow(() -> ApiException.notFound("Workshop"));
    }
}
