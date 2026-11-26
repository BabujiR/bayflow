package com.bayflow.appointment.bookingservices;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bayflow.appointment.application.Locations;
import com.bayflow.appointment.bookingservices.BookingConfirmation.Kind;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.appointment.integration.WorkshopDirectory;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;
import com.bayflow.security.WorkshopHosts;

/**
 * When the shop moves or cancels an online booking, the customer (who may have no account to
 * see it in) is e-mailed; a moved booking gets a new private link (the old one stops working).
 * Best effort: never in the way of the shop's change.
 */
@Component
public class ShopChangeMails {

    private static final Logger log = LoggerFactory.getLogger(ShopChangeMails.class);

    private final WorkshopHosts hosts;
    private final WorkshopDirectory directory;
    private final Locations locations;
    private final BookingConfirmation mails;

    public ShopChangeMails(WorkshopHosts hosts, WorkshopDirectory directory, Locations locations,
            BookingConfirmation mails) {
        this.hosts = hosts;
        this.directory = directory;
        this.locations = locations;
        this.mails = mails;
    }

    public void moved(Appointment a) {
        send(a, Kind.MOVED_BY_SHOP);
    }

    public void cancelled(Appointment a) {
        send(a, Kind.CANCELLED_BY_SHOP);
    }

    private void send(Appointment a, Kind kind) {
        if (!a.isBookedOnline()) {
            return;
        }
        try {
            Optional<Workshop> shop = shopOfRequest();
            List<LocationSettings> active = locations.active();
            Optional<LocationSettings> location = active.stream().filter(l -> l.id().equals(a.getLocationId()))
                    .findFirst();
            if (shop.isEmpty() || location.isEmpty()) {
                log.info("No e-mail to the customer of online booking {}: workshop or location unknown", a.getId());
                return;
            }
            String token = kind == Kind.MOVED_BY_SHOP ? ManageTokens.newToken() : null;
            boolean sent = mails.send(shop.get(), location.get(), active.size() > 1,
                    locations.zoneOf(location.get()), a, kind, token);
            if (sent && token != null) {
                a.useManageToken(ManageTokens.hash(token));
            }
        } catch (RuntimeException e) {
            log.warn("No e-mail to the customer of online booking {}: {}", a.getId(), e.getMessage());
        }
    }

    /** The workshop at the address the staff member is using (the request's host). */
    private Optional<Workshop> shopOfRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        return hosts.slugOf(request).flatMap(directory::bySlug);
    }
}
