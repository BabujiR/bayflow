package com.bayflow.workshop.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.domain.JobCard;
import com.bayflow.workshop.domain.ServiceHistoryEntry;
import com.bayflow.workshop.infrastructure.ServiceHistoryRepository;

import tools.jackson.databind.json.JsonMapper;

/** Writes the immutable history entry when a job completes, and reads a vehicle's history. */
@Service
public class ServiceHistoryService {

    private final ServiceHistoryRepository history;
    private final JsonMapper json;

    public ServiceHistoryService(ServiceHistoryRepository history, JsonMapper json) {
        this.history = history;
        this.json = json;
    }

    /** Called inside the completing transaction, so the card and its history commit together. */
    @Transactional
    public ServiceHistoryEntry record(JobCard completedCard) {
        String details = json.writeValueAsString(ServiceRecord.of(completedCard));
        return history.save(ServiceHistoryEntry.record(completedCard, details));
    }

    /** Newest first. Staff see every entry for the vehicle; customers only entries from their own jobs. */
    @Transactional(readOnly = true)
    public Page<ServiceHistoryEntry> forVehicle(CurrentUser user, UUID vehicleId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "serviceDate").and(Sort.by("id")));
        if (user.isCustomer()) {
            UUID userId;
            try {
                userId = UUID.fromString(user.subject());
            } catch (IllegalArgumentException e) {
                return Page.empty(pageable);
            }
            return history.findByVehicleIdAndCustomerUserId(vehicleId, userId, pageable);
        }
        return history.findByVehicleId(vehicleId, pageable);
    }

    public ServiceRecord details(ServiceHistoryEntry entry) {
        return json.readValue(entry.getDetails(), ServiceRecord.class);
    }
}
