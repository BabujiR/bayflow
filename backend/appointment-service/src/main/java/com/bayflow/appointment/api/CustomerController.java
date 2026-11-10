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

import com.bayflow.appointment.api.AppointmentDtos.CreateCustomerRequest;
import com.bayflow.appointment.api.AppointmentDtos.CustomerResponse;
import com.bayflow.appointment.api.AppointmentDtos.PageResponse;
import com.bayflow.appointment.api.AppointmentDtos.ProfileRequest;
import com.bayflow.appointment.application.CustomerService;
import com.bayflow.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/customers")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create my customer profile (needed before adding vehicles or bookings)")
    public CustomerResponse createProfile(@Valid @RequestBody ProfileRequest request, Authentication auth) {
        return CustomerResponse.from(customers.createOwnProfile(CurrentUser.from(auth), request.name(),
                request.email(), request.phone()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "My customer profile")
    public CustomerResponse myProfile(Authentication auth) {
        return CustomerResponse.from(customers.getOwnProfile(CurrentUser.from(auth)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update my customer profile")
    public CustomerResponse updateProfile(@Valid @RequestBody ProfileRequest request, Authentication auth) {
        return CustomerResponse.from(customers.updateOwnProfile(CurrentUser.from(auth), request.name(),
                request.email(), request.phone()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SERVICE_ADVISOR')")
    @Operation(summary = "Create a customer on their behalf (advisor)")
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return CustomerResponse.from(customers.createForCustomer(request.userId(), request.name(),
                request.email(), request.phone()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN')")
    @Operation(summary = "Search customers by name, email or phone")
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(customers.list(q, Paging.page(page), Paging.size(size)), CustomerResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SERVICE_ADVISOR', 'ADMIN', 'CUSTOMER')")
    @Operation(summary = "A customer (customers can only read their own)")
    public CustomerResponse get(@PathVariable UUID id, Authentication auth) {
        return CustomerResponse.from(customers.get(CurrentUser.from(auth), id));
    }
}
