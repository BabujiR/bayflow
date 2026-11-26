package com.bayflow.appointment.bookingservices;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.application.Callers;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.domain.Vehicle;
import com.bayflow.appointment.infrastructure.AppointmentRepository;
import com.bayflow.appointment.infrastructure.CustomerRepository;
import com.bayflow.appointment.infrastructure.VehicleRepository;
import com.bayflow.security.CurrentUser;

/**
 * A signed-in customer takes their online bookings into their account. The booking's private
 * link proves the e-mail is theirs (accounts' e-mails are not verified). With no profile yet,
 * the online booker's record becomes their profile; otherwise its cars and bookings move to
 * their profile and the record goes.
 */
@Service
public class BookingClaim {

    private static final Logger log = LoggerFactory.getLogger(BookingClaim.class);

    private final AppointmentRepository appointments;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final Clock clock;

    public BookingClaim(AppointmentRepository appointments, CustomerRepository customers, VehicleRepository vehicles,
            Clock clock) {
        this.appointments = appointments;
        this.customers = customers;
        this.vehicles = vehicles;
        this.clock = clock;
    }

    /** The caller's profile, now holding the online booker's cars and bookings. */
    @Transactional
    public Customer claim(CurrentUser user, String token) {
        if (!ManageTokens.looksValid(token)) {
            throw invalid();
        }
        Appointment booking = appointments.findByManageTokenHash(ManageTokens.hash(token)).orElseThrow(
                BookingClaim::invalid);
        UUID me = Callers.userId(user);
        Customer booker = booking.getCustomer();
        if (booker.isOwnedBy(me)) {
            return booker;
        }
        if (booker.getUserId() != null) {
            throw ApiException.conflict("already-claimed", "These bookings are already in another account.");
        }
        Instant now = clock.instant();
        Optional<Customer> mine = customers.findByUserId(me);
        if (mine.isEmpty()) {
            booker.linkUser(me, now);
            log.info("Online booker {} joined account {}", booker.getId(), me);
            return customers.saveAndFlush(booker);
        }
        Customer profile = mine.get();
        for (Vehicle v : vehicles.findAllByOwnerId(booker.getId())) {
            v.moveTo(profile, now);
        }
        for (Appointment a : appointments.findAllByCustomerId(booker.getId())) {
            a.moveTo(profile, now);
        }
        vehicles.flush();
        appointments.flush();
        customers.delete(booker);
        customers.flush();
        log.info("Online booker {} merged into customer {}", booker.getId(), profile.getId());
        return profile;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.NOT_FOUND, "claim-link-invalid", "Not Found",
                "This booking link isn't valid any more, so it can't be added to your account.");
    }
}
