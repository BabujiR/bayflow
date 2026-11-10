package com.bayflow.appointment.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.infrastructure.CustomerRepository;
import com.bayflow.security.CurrentUser;

/** Resolves what the caller may see: advisors and admins see everything, customers only their own data. */
@Component
public class Callers {

    private final CustomerRepository customers;

    public Callers(CustomerRepository customers) {
        this.customers = customers;
    }

    public static UUID userId(CurrentUser user) {
        try {
            return UUID.fromString(user.subject());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid-subject", "The token subject is not a user id.");
        }
    }

    public static boolean seesAll(CurrentUser user) {
        return user.isServiceAdvisor() || user.isAdmin();
    }

    public Optional<Customer> findOwnProfile(CurrentUser user) {
        return customers.findByUserId(userId(user));
    }

    /** The caller's customer profile; customers must create one before adding vehicles or bookings. */
    public Customer requireOwnProfile(CurrentUser user) {
        return findOwnProfile(user).orElseThrow(() -> ApiException.conflict("customer-profile-required",
                "Create your customer profile first (POST /api/v1/customers/me)."));
    }

    public static boolean owns(CurrentUser user, Customer customer) {
        return user.isCustomer() && customer.isOwnedBy(userId(user));
    }
}
