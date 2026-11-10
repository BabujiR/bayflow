package com.bayflow.workshop.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.security.CurrentUser;
import com.bayflow.workshop.api.WorkshopDtos.PageResponse;
import com.bayflow.workshop.api.WorkshopDtos.ServiceHistoryResponse;
import com.bayflow.workshop.application.ServiceHistoryService;
import com.bayflow.workshop.config.WorkshopProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/service-history")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Service history")
public class ServiceHistoryController {

    private final ServiceHistoryService history;
    private final WorkshopProperties properties;

    public ServiceHistoryController(ServiceHistoryService history, WorkshopProperties properties) {
        this.history = history;
        this.properties = properties;
    }

    @GetMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN')")
    @Operation(summary = "A vehicle's completed jobs, newest first",
            description = "Customers see the entries for jobs on their own account.")
    public PageResponse<ServiceHistoryResponse> forVehicle(@PathVariable UUID vehicleId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return PageResponse.from(
                history.forVehicle(CurrentUser.from(auth), vehicleId, Paging.page(page), Paging.size(size)),
                entry -> ServiceHistoryResponse.from(entry, history.details(entry), properties.getCurrency()));
    }
}
