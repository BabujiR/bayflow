package com.bayflow.appointment.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;

import com.bayflow.appointment.application.VehicleService.VehicleDetails;
import com.bayflow.appointment.domain.Appointment;
import com.bayflow.appointment.domain.AppointmentStatus;
import com.bayflow.appointment.domain.Customer;
import com.bayflow.appointment.domain.ServiceType;
import com.bayflow.appointment.domain.Vehicle;

/** Request and response bodies of the appointment API. */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    static final String PHONE = "^[+0-9 ()\\-]{5,30}$";

    // ---- customers ----------------------------------------------------------------

    public record ProfileRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE, message = "must be a phone number") String phone) {
    }

    /** Advisor-created customer; {@code userId} links an existing login (optional). */
    public record CreateCustomerRequest(
            UUID userId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE, message = "must be a phone number") String phone) {
    }

    public record CustomerResponse(UUID id, UUID userId, String name, String email, String phone,
            Instant createdAt) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getUserId(), c.getName(), c.getEmail(), c.getPhone(),
                    c.getCreatedAt());
        }
    }

    // ---- vehicles -------------------------------------------------------------------

    /** {@code customerId} is required for advisors and ignored for customers. */
    public record CreateVehicleRequest(
            UUID customerId,
            @NotBlank @Pattern(regexp = "(?i)^[A-HJ-NPR-Z0-9]{17}$", message = "must be a 17-character VIN")
            String vin,
            @NotBlank @Size(max = 20) String registration,
            @NotBlank @Size(max = 50) String make,
            @NotBlank @Size(max = 50) String model,
            @NotNull @Min(1900) @Max(2100) Integer year,
            @NotNull @PositiveOrZero Integer mileage) {

        VehicleDetails details() {
            return new VehicleDetails(registration, make, model, year, mileage);
        }
    }

    public record UpdateVehicleRequest(
            @NotBlank @Size(max = 20) String registration,
            @NotBlank @Size(max = 50) String make,
            @NotBlank @Size(max = 50) String model,
            @NotNull @Min(1900) @Max(2100) Integer year,
            @NotNull @PositiveOrZero Integer mileage) {

        VehicleDetails details() {
            return new VehicleDetails(registration, make, model, year, mileage);
        }
    }

    public record VehicleResponse(UUID id, UUID customerId, String vin, String registration, String make,
            String model, int year, int mileage, Instant createdAt) {

        public static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getOwner().getId(), v.getVin(), v.getRegistration(),
                    v.getMake(), v.getModel(), v.getYear(), v.getMileage(), v.getCreatedAt());
        }
    }

    // ---- appointments ---------------------------------------------------------------

    public record BookAppointmentRequest(
            @NotNull UUID vehicleId,
            @NotNull Instant scheduledAt,
            @Min(Appointment.MIN_DURATION_MINUTES) @Max(Appointment.MAX_DURATION_MINUTES) Integer durationMinutes,
            @NotNull ServiceType serviceType,
            @Size(max = 1000) String notes) {
    }

    public record RescheduleRequest(
            @NotNull Instant scheduledAt,
            @Min(Appointment.MIN_DURATION_MINUTES) @Max(Appointment.MAX_DURATION_MINUTES) Integer durationMinutes) {
    }

    public record CheckInRequest(@NotNull @PositiveOrZero Integer mileage) {
    }

    public record VehicleSummary(UUID id, String vin, String registration, String make, String model, int year) {

        static VehicleSummary from(Vehicle v) {
            return new VehicleSummary(v.getId(), v.getVin(), v.getRegistration(), v.getMake(), v.getModel(),
                    v.getYear());
        }
    }

    public record AppointmentResponse(UUID id, UUID customerId, String customerName, VehicleSummary vehicle,
            Instant scheduledAt, Instant endsAt, int durationMinutes, ServiceType serviceType, String notes,
            AppointmentStatus status, Integer arrivalMileage, Instant confirmedAt, Instant arrivedAt,
            Instant checkedInAt, Instant cancelledAt, UUID jobCardId, Instant createdAt, Instant updatedAt) {

        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(a.getId(), a.getCustomer().getId(), a.getCustomer().getName(),
                    VehicleSummary.from(a.getVehicle()), a.getScheduledAt(), a.getEndsAt(), a.getDurationMinutes(),
                    a.getServiceType(), a.getNotes(), a.getStatus(), a.getArrivalMileage(), a.getConfirmedAt(),
                    a.getArrivedAt(), a.getCheckedInAt(), a.getCancelledAt(), a.getJobCardId(), a.getCreatedAt(),
                    a.getUpdatedAt());
        }
    }

    // ---- paging ------------------------------------------------------------------------

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
