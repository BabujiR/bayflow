package com.bayflow.appointment.application;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.infrastructure.CustomerRepository;
import com.bayflow.security.CurrentUser;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final Callers callers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Callers callers, Clock clock) {
        this.customers = customers;
        this.callers = callers;
        this.clock = clock;
    }

    /** A signed-in customer creates the profile linked to their account. */
    @Transactional
    public Customer createOwnProfile(CurrentUser user, String name, String email, String phone) {
        UUID userId = Callers.userId(user);
        if (customers.existsByUserId(userId)) {
            throw ApiException.conflict("profile-exists", "You already have a customer profile.");
        }
        return customers.save(Customer.create(userId, name, email, phone, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Customer getOwnProfile(CurrentUser user) {
        return callers.findOwnProfile(user).orElseThrow(() -> ApiException.notFound("Customer profile"));
    }

    @Transactional
    public Customer updateOwnProfile(CurrentUser user, String name, String email, String phone) {
        Customer customer = getOwnProfile(user);
        customer.update(name, email, phone, clock.instant());
        return customer;
    }

    /** An advisor creates a customer, optionally linked to an existing login. */
    @Transactional
    public Customer createForCustomer(UUID userId, String name, String email, String phone) {
        if (userId != null && customers.existsByUserId(userId)) {
            throw ApiException.conflict("profile-exists", "That user already has a customer profile.");
        }
        return customers.save(Customer.create(userId, name, email, phone, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").and(Sort.by("id")));
        if (!StringUtils.hasText(query)) {
            return customers.findAll(pageable);
        }
        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
        return customers.search(pattern, pageable);
    }

    /** Advisors and admins see any customer; a customer only their own profile. */
    @Transactional(readOnly = true)
    public Customer get(CurrentUser user, UUID id) {
        Customer customer = customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer"));
        if (Callers.seesAll(user) || Callers.owns(user, customer)) {
            return customer;
        }
        throw ApiException.notFound("Customer");
    }
}
