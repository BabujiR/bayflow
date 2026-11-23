package com.bayflow.workshop.reports;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.workshop.reports.ReportDtos.AdvisorRow;
import com.bayflow.workshop.reports.ReportDtos.DailyResponse;
import com.bayflow.workshop.reports.ReportDtos.DeclinedRow;
import com.bayflow.workshop.reports.ReportDtos.LocationRow;
import com.bayflow.workshop.reports.ReportDtos.PartRow;
import com.bayflow.workshop.reports.ReportDtos.PaymentsResponse;
import com.bayflow.workshop.reports.ReportDtos.RowsResponse;
import com.bayflow.workshop.reports.ReportDtos.SummaryResponse;
import com.bayflow.workshop.reports.ReportDtos.TaxResponse;
import com.bayflow.workshop.reports.ReportDtos.TechnicianRow;
import com.bayflow.workshop.reports.ReportDtos.UnpaidResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Owner dashboard and reports. Periods are half-open [from, to) as ISO-8601 instants; the
 * web app works out the boundaries in the workshop's time zone. Admins see every location,
 * managers the locations they manage; the X-Bayflow-Location header narrows to one.
 */
@RestController
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    @Operation(summary = "Car count, sales, ARO, hours, close ratio for the period and the one before; open work now")
    public SummaryResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.summary(ReportPeriod.of(from, to));
    }

    @GetMapping("/daily")
    @Operation(summary = "Completed sales per day in the given time zone")
    public DailyResponse daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String zone) {
        return reports.daily(ReportPeriod.of(from, to), ReportPeriod.zone(zone));
    }

    @GetMapping("/technicians")
    @Operation(summary = "Hours and labour value per mechanic, by the time the hours were logged")
    public List<TechnicianRow> technicians(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.technicians(ReportPeriod.of(from, to));
    }

    @GetMapping("/advisors")
    @Operation(summary = "Estimates sent, approved and declined value, close ratio and sales per advisor")
    public List<AdvisorRow> advisors(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.advisors(ReportPeriod.of(from, to));
    }

    @GetMapping("/locations")
    @Operation(summary = "Completed sales per location")
    public List<LocationRow> locations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.locations(ReportPeriod.of(from, to));
    }

    @GetMapping("/declined")
    @Operation(summary = "Recommendations declined in the period (up to 1000), for follow-up")
    public RowsResponse<DeclinedRow> declined(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.declined(ReportPeriod.of(from, to));
    }

    @GetMapping("/payments")
    @Operation(summary = "Payments and refunds received in the period (up to 1000), with totals per method")
    public PaymentsResponse payments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.payments(ReportPeriod.of(from, to));
    }

    @GetMapping("/unpaid")
    @Operation(summary = "Issued invoices still owed, oldest first, with ageing (now, not for a period)")
    public UnpaidResponse unpaid() {
        return reports.unpaid();
    }

    @GetMapping("/tax")
    @Operation(summary = "Tax on the invoices issued in the period, per category and rate")
    public TaxResponse tax(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.tax(ReportPeriod.of(from, to));
    }

    @GetMapping("/parts")
    @Operation(summary = "Parts fitted in the period (up to 1000)")
    public RowsResponse<PartRow> parts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.parts(ReportPeriod.of(from, to));
    }
}
