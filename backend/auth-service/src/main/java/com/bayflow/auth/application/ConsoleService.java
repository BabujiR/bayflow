package com.bayflow.auth.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.DemoRequest;
import com.bayflow.auth.infrastructure.DemoRequestRepository;
import com.bayflow.security.CurrentUser;

/** The Ramplight console: demo requests (invitations and workshops follow in the next steps). */
@Service
public class ConsoleService {

    private final DemoRequestRepository demos;
    private final Clock clock;

    public ConsoleService(DemoRequestRepository demos, Clock clock) {
        this.demos = demos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DemoRequest> demoRequests(DemoRequest.Status status, String query, int page, int size) {
        String like = query == null || query.isBlank() ? null
                : "%" + query.strip().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return demos.search(status, like, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public DemoRequest demoRequest(UUID id) {
        return demos.findById(id).orElseThrow(() -> ApiException.notFound("Demo request"));
    }

    /** {@code version}: the one read (409 when a teammate changed it meanwhile). */
    @Transactional
    public DemoRequest update(CurrentUser user, UUID id, long version, DemoRequest.Status status, String notes) {
        DemoRequest request = demoRequest(id);
        if (request.getVersion() != null && request.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "Someone else changed this request. Reload and try again.");
        }
        if (status == DemoRequest.Status.INVITED || status == DemoRequest.Status.ONBOARDED) {
            if (request.getStatus() != status) {
                throw ApiException.badRequest("status-set-by-invitation",
                        "Invited and Onboarded are set by sending a setup invite.");
            }
        }
        request.update(status, notes, user.username(), clock.instant());
        return demos.saveAndFlush(request);
    }
}
