package com.bayflow.appointment.bookingservices;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.appointment.application.ApiException;
import com.bayflow.appointment.application.Locations;
import com.bayflow.appointment.bookingservices.BookingService.Details;
import com.bayflow.security.TenantContext;

/**
 * The shop's list of services customers can book online. A shop with none yet starts with a few
 * common ones (no prices: the shop sets its own); "Something else" is never stored, the booking
 * page always adds it.
 */
@Service
public class BookingCatalog {

    static final BigDecimal MAX_PRICE = new BigDecimal("99999.99");

    /** The starting list: code, name, description. */
    static final List<String[]> STARTERS = List.of(
            new String[] { "oil-change", "Oil change", "Oil and filter, fluids topped up" },
            new String[] { "routine-service", "Routine service", "Scheduled maintenance for your mileage" },
            new String[] { "brakes", "Brakes", "Pads, discs, noises or vibration" },
            new String[] { "tires", "Tires", "Fit, rotate, balance or repair" },
            new String[] { "inspection", "Inspection", "A full check of your car" },
            new String[] { "diagnostic", "Diagnostic", "A warning light, or something not right" });

    private final BookingServiceRepository services;
    private final Locations locations;
    private final Clock clock;

    public BookingCatalog(BookingServiceRepository services, Locations locations, Clock clock) {
        this.services = services;
        this.locations = locations;
        this.clock = clock;
    }

    /** Every service in the shop's order, hidden ones too (Settings). Seeds the starters once. */
    public List<BookingService> list() {
        if (services.count() == 0) {
            UUID tenant = TenantContext.require();
            Instant now = clock.instant();
            for (int i = 0; i < STARTERS.size(); i++) {
                String[] s = STARTERS.get(i);
                services.insertStarter(UUID.randomUUID(), tenant, s[0], s[1], s[2], i + 1, now);
            }
        }
        return services.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public BookingService create(Details details) {
        Details checked = check(details);
        BookingService created = BookingService.create(uniqueCode(checked.name()), checked,
                services.maxSortOrder() + 1, clock.instant());
        return services.saveAndFlush(created);
    }

    /** {@code version}: the one read (409 when someone changed it meanwhile). */
    @Transactional
    public BookingService update(UUID id, long version, Details details) {
        BookingService service = find(id);
        if (service.getVersion() != null && service.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "Someone else changed this service. Reload and try again.");
        }
        service.update(check(details), clock.instant());
        return services.saveAndFlush(service);
    }

    /** The shop's new order: every service's id, first to last. */
    @Transactional
    public List<BookingService> reorder(List<UUID> ids) {
        List<BookingService> all = services.findAllByOrderBySortOrderAscNameAsc();
        Map<UUID, BookingService> byId = all.stream()
                .collect(Collectors.toMap(BookingService::getId, Function.identity()));
        if (ids.size() != all.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw ApiException.badRequest("invalid-order", "Send every service once, in the new order.");
        }
        Instant now = clock.instant();
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo(i + 1, now);
        }
        services.saveAllAndFlush(all);
        return services.findAllByOrderBySortOrderAscNameAsc();
    }

    private BookingService find(UUID id) {
        return services.findById(id).orElseThrow(() -> ApiException.notFound("Service"));
    }

    private Details check(Details d) {
        if (d.priceKind().hasAmount()) {
            if (d.price() == null || d.price().signum() < 0 || d.price().compareTo(MAX_PRICE) > 0
                    || d.price().scale() > 2) {
                throw ApiException.badRequest("invalid-price", "Enter a price between 0 and 99,999.99.");
            }
        }
        if (d.durationMinutes() != null && (d.durationMinutes() < 5 || d.durationMinutes() > 1440)) {
            throw ApiException.badRequest("invalid-duration", "The typical time is 5 minutes to 24 hours.");
        }
        if (d.locationId() != null) {
            locations.requireActive(d.locationId());
        }
        return d;
    }

    /** "Wheel alignment" → "wheel-alignment" (then "-2", "-3"… if taken). */
    String uniqueCode(String name) {
        String base = slug(name);
        String code = base;
        for (int n = 2; services.existsByCode(code); n++) {
            String suffix = "-" + n;
            code = base.substring(0, Math.min(base.length(), 40 - suffix.length())) + suffix;
        }
        return code;
    }

    /**
     * "Brake & Rotor Check" → "brake-rotor-check", "Café crème" → "cafe-creme": lower-case letters
     * and digits, one hyphen between words, at most 40 characters. One pass over the name, no
     * regular expressions (names come from users).
     */
    static String slug(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        StringBuilder slug = new StringBuilder(40);
        boolean gap = false;
        for (int i = 0; i < decomposed.length(); i++) {
            char c = Character.toLowerCase(decomposed.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                boolean hyphen = gap && !slug.isEmpty();
                if (slug.length() + (hyphen ? 2 : 1) > 40) {
                    break;
                }
                if (hyphen) {
                    slug.append('-');
                }
                slug.append(c);
                gap = false;
            } else if (!isAccent(c)) {
                gap = true;
            }
        }
        return slug.isEmpty() ? "service" : slug.toString();
    }

    /** An accent split off its letter by NFD ("é" → "e" + U+0301): dropped, not a word break. */
    private static boolean isAccent(char c) {
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }
}
