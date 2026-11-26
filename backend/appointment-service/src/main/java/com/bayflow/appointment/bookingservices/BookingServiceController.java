package com.bayflow.appointment.bookingservices;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.appointment.bookingservices.BookingService.Details;
import com.bayflow.appointment.bookingservices.BookingService.PriceKind;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Settings → Online booking: the services customers can book (workshop ADMIN edits; staff read). */
@RestController
@RequestMapping("/api/v1/booking-services")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Online booking")
public class BookingServiceController {

    public record BookingServiceResponse(UUID id, String code, String name, String description, PriceKind priceKind,
            BigDecimal price, Integer durationMinutes, UUID locationId, boolean active, int sortOrder,
            Instant updatedAt, long version) {

        static BookingServiceResponse from(BookingService s) {
            return new BookingServiceResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(),
                    s.getPriceKind(), s.getPrice(), s.getDurationMinutes(), s.getLocationId(), s.isActive(),
                    s.getSortOrder(), s.getUpdatedAt(), s.getVersion() == null ? 0 : s.getVersion());
        }
    }

    /** {@code price}: only with FROM and FIXED. {@code locationId} null: every location. */
    public record BookingServiceRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 200) String description,
            @NotNull PriceKind priceKind,
            BigDecimal price,
            @Min(5) @Max(1440) Integer durationMinutes,
            UUID locationId,
            boolean active,
            Long version) {

        Details details() {
            return new Details(name, description, priceKind, price, durationMinutes, locationId, active);
        }
    }

    public record OrderRequest(@NotEmpty List<@NotNull UUID> ids) {
    }

    private final BookingCatalog catalog;

    public BookingServiceController(BookingCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_ADVISOR')")
    @Operation(summary = "The shop's bookable services in its order, hidden ones too (a new shop gets a starting list)")
    public List<BookingServiceResponse> list() {
        return catalog.list().stream().map(BookingServiceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a service (goes to the end of the list)")
    public BookingServiceResponse create(@Valid @RequestBody BookingServiceRequest request) {
        return BookingServiceResponse.from(catalog.create(request.details()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a service, or hide / show it (active); send the version read")
    public BookingServiceResponse update(@PathVariable UUID id, @Valid @RequestBody BookingServiceRequest request) {
        return BookingServiceResponse.from(catalog.update(id, request.version() == null ? -1 : request.version(),
                request.details()));
    }

    @PutMapping("/order")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Put the services in a new order (every id once, first to last)")
    public List<BookingServiceResponse> reorder(@Valid @RequestBody OrderRequest request) {
        return catalog.reorder(request.ids()).stream().map(BookingServiceResponse::from).toList();
    }
}
