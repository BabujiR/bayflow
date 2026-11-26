package com.bayflow.appointment.bookingservices;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.integration.WorkshopDirectory;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;
import com.bayflow.security.LocationContext;
import com.bayflow.security.LocationScope;
import com.bayflow.security.TenantContext;
import com.bayflow.security.WorkshopHosts;

/** The public booking pages' workshop: the one at the request's address. */
@Component
public class PublicShop {

    /** Same header the web app sends on every request (a cross-site form can't set it). */
    static final String CLIENT_HEADER = "X-Bayflow-Client";

    private final WorkshopHosts hosts;
    private final WorkshopDirectory directory;
    private final PublicBookingLimiter limiter;

    public PublicShop(WorkshopHosts hosts, WorkshopDirectory directory, PublicBookingLimiter limiter) {
        this.hosts = hosts;
        this.directory = directory;
        this.limiter = limiter;
    }

    /** The workshop at this address: 404 when none, 403 while it is suspended, 503 if it can't be asked. */
    public Workshop of(HttpServletRequest request) {
        String slug = hosts.slugOf(request).orElseThrow(PublicShop::noWorkshop);
        Workshop workshop;
        try {
            workshop = directory.bySlug(slug).orElseThrow(PublicShop::noWorkshop);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "booking-unavailable", "Service Unavailable",
                    "Online booking isn't available right now. Please try again shortly.");
        }
        if (!workshop.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "workshop-suspended", "Forbidden",
                    "This workshop isn't taking online bookings right now.");
        }
        return workshop;
    }

    /** For a change (POST): the web app's header, then the per-address limit. */
    public Workshop forChange(HttpServletRequest request) {
        if (request.getHeader(CLIENT_HEADER) == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "missing-client-header", "Forbidden",
                    "Send the X-Bayflow-Client header.");
        }
        Workshop workshop = of(request);
        limiter.check(workshop.id() + "|" + request.getRemoteAddr());
        return workshop;
    }

    /** Runs {@code work} inside the workshop, at all of its locations. */
    public static <T> T inside(Workshop workshop, Supplier<T> work) {
        return TenantContext.call(workshop.id(), () -> LocationContext.call(LocationScope.ALL, work));
    }

    private static ApiException noWorkshop() {
        return new ApiException(HttpStatus.NOT_FOUND, "workshop-not-found", "Not Found",
                "There is no workshop at this address.");
    }
}
