package com.bayflow.appointment.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.api.AppointmentDtos.CreateVehicleRequest;
import com.bayflow.appointment.api.AppointmentDtos.PageResponse;
import com.bayflow.appointment.api.AppointmentDtos.UpdateVehicleRequest;
import com.bayflow.appointment.api.AppointmentDtos.VehicleResponse;
import com.bayflow.appointment.application.VehicleService;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/vehicles")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Vehicles")
public class VehicleController {

    private final VehicleService vehicles;

    public VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Register a vehicle (customers: their own; advisors: pass customerId)")
    public VehicleResponse register(@Valid @RequestBody CreateVehicleRequest request, Authentication auth) {
        return VehicleResponse.from(
                vehicles.register(CurrentUser.from(auth), request.customerId(), request.vin(), request.details()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "List vehicles (customers see their own; staff can filter by customerId)")
    public PageResponse<VehicleResponse> list(@RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return PageResponse.from(vehicles.list(CurrentUser.from(auth), customerId, Paging.page(page),
                Paging.size(size)), VehicleResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "A vehicle")
    public VehicleResponse get(@PathVariable UUID id, Authentication auth) {
        return VehicleResponse.from(vehicles.get(CurrentUser.from(auth), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SERVICE_ADVISOR')")
    @Operation(summary = "Update registration, make, model, year or mileage (VIN cannot change)")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request,
            Authentication auth) {
        return VehicleResponse.from(vehicles.update(CurrentUser.from(auth), id, request.details()));
    }
}
