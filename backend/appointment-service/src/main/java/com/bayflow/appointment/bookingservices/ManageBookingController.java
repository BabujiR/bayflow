package com.bayflow.appointment.bookingservices;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.bookingservices.ManageBooking.View;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.integration.WorkshopDirectory.Workshop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * An online booking's private link (<workshop address>/book/manage?token=…): no sign-in; the
 * token is the key. Sent in request bodies, so it stays out of URLs in server logs.
 */
@RestController
@RequestMapping("/api/v1/booking/manage")
@Tag(name = "Online booking (public)")
public class ManageBookingController {

    public record TokenRequest(@NotBlank @Size(max = 64) String token) {
    }

    /** Own OpenAPI name: another API type is also called RescheduleRequest. */
    @Schema(name = "ManageRescheduleRequest")
    public record RescheduleRequest(@NotBlank @Size(max = 64) String token, @NotNull Instant startsAt) {
    }

    public record CancelRequest(@NotBlank @Size(max = 64) String token, @Size(max = 300) String reason) {
    }

    /**
     * {@code blocked}: why it can't be changed here (NONE: it can). {@code hasAccount}: the
     * booking is already in a customer account.
     */
    public record ManagedBooking(AppointmentStatus status, Instant startsAt, String timeZone, int durationMinutes,
            String locationCode, String locationName, String address, boolean manyLocations, String serviceName,
            String car, String shopName, String firstName, boolean canChange, ManageBooking.Blocked blocked,
            int noticeMinutes, int reschedulesLeft, boolean cancelledByCustomer, boolean hasAccount) {

        static ManagedBooking of(Workshop shop, View v) {
            Appointment a = v.appointment();
            return new ManagedBooking(a.getStatus(), a.getScheduledAt(), v.zone().getId(), a.getDurationMinutes(),
                    v.location() == null ? null : v.location().code(),
                    v.location() == null ? null : v.location().name(),
                    v.location() == null ? null : v.location().address(), v.manyLocations(), a.getServiceName(),
                    BookingConfirmation.describe(a.getVehicle()), shop.shownName(),
                    a.getCustomer().getName().split(" ")[0], v.canChange(), v.blocked(), v.noticeMinutes(),
                    v.reschedulesLeft(), a.isCancelledByCustomer(), a.getCustomer().getUserId() != null);
        }
    }

    private final PublicShop shops;
    private final ManageBooking manage;

    public ManageBookingController(PublicShop shops, ManageBooking manage) {
        this.shops = shops;
        this.manage = manage;
    }

    @PostMapping
    @Operation(summary = "The booking a private link names")
    public ManagedBooking view(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        Workshop shop = shops.of(request);
        return PublicShop.inside(shop, () -> ManagedBooking.of(shop, manage.view(body.token())));
    }

    @PostMapping("/reschedule")
    @Operation(summary = "Move it to another free time (at most 3 times; not inside the shop's notice)")
    public ManagedBooking reschedule(@Valid @RequestBody RescheduleRequest body, HttpServletRequest request) {
        Workshop shop = shops.forChange(request);
        return PublicShop.inside(shop,
                () -> ManagedBooking.of(shop, manage.reschedule(shop, body.token(), body.startsAt())));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel it (not inside the shop's notice); the reason is shown to the shop")
    public ManagedBooking cancel(@Valid @RequestBody CancelRequest body, HttpServletRequest request) {
        Workshop shop = shops.forChange(request);
        return PublicShop.inside(shop,
                () -> ManagedBooking.of(shop, manage.cancel(shop, body.token(), body.reason())));
    }
}
