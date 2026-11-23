package com.bayflow.workshop.invoicing;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bayflow.workshop.invoicing.InvoiceExportService.InvoiceExport;
import com.bayflow.workshop.reports.ReportPeriod;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Invoices and payments of a period for the accountant (managers and admins). */
@RestController
@RequestMapping("/api/v1/invoices")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Invoices")
public class InvoiceExportController {

    private final InvoiceExportService exports;

    public InvoiceExportController(InvoiceExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Invoices issued and payments received in [from, to), for CSV export",
            description = "Voided invoices and drafts are left out. At most 366 days; 400 when the period holds "
                    + "more than 5,000 invoices.")
    public InvoiceExport export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return exports.export(ReportPeriod.of(from, to));
    }
}
