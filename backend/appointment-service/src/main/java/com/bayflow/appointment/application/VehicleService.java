package com.bayflow.appointment.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.domain.Vehicle;
import com.bayflow.appointment.infrastructure.CustomerRepository;
import com.bayflow.appointment.infrastructure.VehicleRepository;
import com.bayflow.security.CurrentUser;

@Service
public class VehicleService {

    /** Fields a vehicle can be created or updated with. */
    public record VehicleDetails(String registration, String make, String model, int year, int mileage) {
    }

    private final VehicleRepository vehicles;
    private final CustomerRepository customers;
    private final Callers callers;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, CustomerRepository customers, Callers callers, Clock clock) {
        this.vehicles = vehicles;
        this.customers = customers;
        this.callers = callers;
        this.clock = clock;
    }

    /** Customers register vehicles for themselves; advisors for the given customer. */
    @Transactional
    public Vehicle register(CurrentUser user, UUID customerId, String vin, VehicleDetails details) {
        Customer owner;
        if (Callers.seesAll(user)) {
            if (customerId == null) {
                throw ApiException.badRequest("customer-required", "customerId is required.");
            }
            owner = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer"));
        } else {
            owner = callers.requireOwnProfile(user);
        }
        String normalizedVin = Vehicle.normalizeVin(vin);
        if (!Vehicle.VIN.matcher(normalizedVin).matches()) {
            throw ApiException.badRequest("invalid-vin", "A VIN has 17 letters and digits (no I, O or Q).");
        }
        if (vehicles.existsByVin(normalizedVin)) {
            throw ApiException.conflict("vin-taken", "A vehicle with this VIN is already registered.");
        }
        return vehicles.save(Vehicle.register(owner, normalizedVin, details.registration(), details.make(),
                details.model(), details.year(), details.mileage(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<Vehicle> list(CurrentUser user, UUID customerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        if (Callers.seesAll(user)) {
            return customerId == null ? vehicles.findAll(pageable) : vehicles.findByOwnerId(customerId, pageable);
        }
        Optional<Customer> own = callers.findOwnProfile(user);
        return own.map(c -> vehicles.findByOwnerId(c.getId(), pageable)).orElse(Page.empty(pageable));
    }

    @Transactional(readOnly = true)
    public Vehicle get(CurrentUser user, UUID id) {
        Vehicle vehicle = vehicles.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle"));
        if (Callers.seesAll(user) || Callers.owns(user, vehicle.getOwner())) {
            return vehicle;
        }
        throw ApiException.notFound("Vehicle");
    }

    @Transactional
    public Vehicle update(CurrentUser user, UUID id, VehicleDetails details) {
        Vehicle vehicle = get(user, id);
        vehicle.update(details.registration(), details.make(), details.model(), details.year(), details.mileage(),
                clock.instant());
        return vehicle;
    }
}
