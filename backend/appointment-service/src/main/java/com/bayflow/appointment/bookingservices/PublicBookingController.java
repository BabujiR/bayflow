package com.bayflow.appointment.bookingservices;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.application.Locations;
import com.bayflow.appointment.bookingservices.BookingService.PriceKind;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.integration.LocationsClient.LocationSettings;
import com.bayflow.appointment.integration.WorkshopDirectory;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;
import com.bayflow.security.LocationContext;
import com.bayflow.security.LocationScope;
import com.bayflow.security.TenantContext;
import com.bayflow.security.WorkshopHosts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The public booking page (<workshop address>/book): no sign-in. The workshop is the one at the
 * request's address; everything runs inside it, at all of its locations.
 */
@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "Online booking (public)")
public class PublicBookingController {

    /** Same header the web app sends on every request (a cross-site form can't set it). */
    static final String CLIENT_HEADER = "X-Bayflow-Client";

    public record PublicLocation(String code, String name, String address, String timeZone) {
    }

    /** {@code locations}: the location codes offering it; null = all. */
    public record PublicService(String code, String name, String description, PriceKind priceKind, BigDecimal price,
            Integer durationMinutes, List<String> locations) {
    }

    public record Options(String shopName, String currency, List<PublicLocation> locations,
            List<PublicService> services) {
    }

    public record AvailabilityDay(LocalDate date, List<Instant> slots) {
    }

    public record Availability(String location, String timeZone, List<AvailabilityDay> days) {
    }

    /**
     * {@code service} null: "Something else" (then {@code description}). The car: {@code vin}, or
     * {@code year}, {@code make} and {@code model}. {@code website} must stay empty (a field only
     * bots fill in).
     */
    public record BookingRequest(
            @NotBlank @Size(max = 20) String location,
            @Size(max = 40) String service,
            @Size(max = 500) String description,
            @Size(max = 17) String vin,
            @Min(1950) @Max(2100) Integer year,
            @Size(max = 50) String make,
            @Size(max = 50) String model,
            @NotNull Instant startsAt,
            @NotBlank @Size(max = 50) String firstName,
            @NotBlank @Size(max = 50) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^[0-9+()\\-. ]{7,30}$", message = "Enter a phone number.") String phone,
            boolean smsConsent,
            @Size(max = 200) String website) {
    }

    /** {@code emailed}: a confirmation e-mail is on its way. */
    public record Booked(Instant startsAt, String timeZone, String locationName, String serviceName, String email,
            boolean emailed) {
    }

    private final WorkshopHosts hosts;
    private final WorkshopDirectory directory;
    private final Locations locations;
    private final BookingCatalog catalog;
    private final BookingHours hours;
    private final OnlineBooking booking;
    private final BookingConfirmation confirmation;
    private final PublicBookingLimiter limiter;

    public PublicBookingController(WorkshopHosts hosts, WorkshopDirectory directory, Locations locations,
            BookingCatalog catalog, BookingHours hours, OnlineBooking booking,
            BookingConfirmation confirmation, PublicBookingLimiter limiter) {
        this.hosts = hosts;
        this.directory = directory;
        this.locations = locations;
        this.catalog = catalog;
        this.hours = hours;
        this.booking = booking;
        this.confirmation = confirmation;
        this.limiter = limiter;
    }

    @GetMapping("/options")
    @Operation(summary = "What this workshop offers online: its locations and services")
    public Options options(HttpServletRequest request) {
        Workshop workshop = workshopOf(request);
        return inside(workshop, () -> {
            List<LocationSettings> active = locations.active();
            Map<UUID, String> codes = active.stream().collect(Collectors.toMap(LocationSettings::id,
                    LocationSettings::code));
            // The shop's list (a shop that never opened Settings gets its starting services).
            List<PublicService> offered = catalog.list().stream()
                    .filter(BookingService::isActive)
                    .filter(s -> s.getLocationId() == null || codes.containsKey(s.getLocationId()))
                    .map(s -> new PublicService(s.getCode(), s.getName(), s.getDescription(), s.getPriceKind(),
                            s.getPrice(), s.getDurationMinutes(),
                            s.getLocationId() == null ? null : List.of(codes.get(s.getLocationId()))))
                    .toList();
            String currency = active.isEmpty() ? "USD" : active.getFirst().currency();
            return new Options(workshop.shownName(), currency,
                    active.stream().map(l -> new PublicLocation(l.code(), l.name(), l.address(),
                            locations.zoneOf(l).getId())).toList(),
                    offered);
        });
    }

    @GetMapping("/availability")
    @Operation(summary = "Free start times at a location, from a day, for up to 14 days")
    public Availability availability(@RequestParam String location,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "7") int days, HttpServletRequest request) {
        Workshop workshop = workshopOf(request);
        return inside(workshop, () -> {
            LocationSettings l = locationByCode(location);
            ZoneId zone = locations.zoneOf(l);
            return new Availability(l.code(), zone.getId(), hours.freeSlots(l.id(), zone, from, Math.max(days, 1))
                    .stream().map(d -> new AvailabilityDay(d.date(), d.slots())).toList());
        });
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book without signing in (confirmed at once; rate-limited per address)",
            description = "Send the X-Bayflow-Client header like the web app.")
    public Booked book(@Valid @RequestBody BookingRequest body, HttpServletRequest request) {
        if (request.getHeader(CLIENT_HEADER) == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "missing-client-header", "Forbidden",
                    "Send the X-Bayflow-Client header.");
        }
        Workshop workshop = workshopOf(request);
        limiter.check(workshop.id() + "|" + request.getRemoteAddr());
        if (body.website() != null && !body.website().isBlank()) {
            // A bot filled in the hidden field: answer as if it worked, book nothing.
            return new Booked(body.startsAt(), "UTC", "", "", body.email(), false);
        }
        return inside(workshop, () -> {
            List<LocationSettings> active = locations.active();
            LocationSettings l = locationByCode(body.location());
            ZoneId zone = locations.zoneOf(l);
            Appointment saved = booking.book(l, zone, new OnlineBooking.Request(body.service(), body.description(),
                    body.vin(), body.year(), body.make(), body.model(), body.startsAt(), body.firstName(),
                    body.lastName(), body.email(), body.phone(), body.smsConsent()));
            boolean emailed = confirmation.send(workshop.id(), workshop.shownName(), l, active.size() > 1, zone, saved);
            return new Booked(saved.getScheduledAt(), zone.getId(), l.name(), saved.getServiceName(),
                    saved.getCustomer().getEmail(), emailed);
        });
    }

    private LocationSettings locationByCode(String code) {
        return locations.active().stream().filter(l -> l.code().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("invalid-location", "Choose a location."));
    }

    /** The workshop at this address: 404 when none, 403 while it is suspended, 503 if it can't be asked. */
    private Workshop workshopOf(HttpServletRequest request) {
        String slug = hosts.slugOf(request).orElseThrow(BookingErrors::noWorkshop);
        Workshop workshop;
        try {
            workshop = directory.bySlug(slug).orElseThrow(BookingErrors::noWorkshop);
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

    private static <T> T inside(Workshop workshop, Supplier<T> work) {
        return TenantContext.call(workshop.id(), () -> LocationContext.call(LocationScope.ALL, work));
    }

    private static final class BookingErrors {
        static ApiException noWorkshop() {
            return new ApiException(HttpStatus.NOT_FOUND, "workshop-not-found", "Not Found",
                    "There is no workshop at this address.");
        }
    }
}
