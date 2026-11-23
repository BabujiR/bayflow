package com.bayflow.workshop.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response bodies of the owner dashboard and reports. Money is in the workshop's currency;
 * every figure covers the locations the caller may see (and the one picked in the switcher).
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    // Sales and estimate figures for one period.
    public record PeriodMetrics(
            // Completed job cards (car count).
            long carCount,
            BigDecimal sales, BigDecimal partsSales, BigDecimal labourSales,
            // Average repair order: sales ÷ car count.
            BigDecimal averageRepairOrder,
            // Hours billed on completed cards.
            BigDecimal labourHours,
            // Effective labour rate: labour sales ÷ hours billed.
            BigDecimal effectiveLabourRate,
            // Recommendations decided in the period (approved + declined).
            long itemsApproved, long itemsDeclined,
            BigDecimal approvedValue, BigDecimal declinedValue,
            BigDecimal hoursApproved, BigDecimal hoursDeclined,
            // approved value ÷ (approved + declined value), 0–1; null with nothing decided.
            BigDecimal closeRatio,
            // Invoiced fees and discounts (sales = parts + labour + fees − discounts, before tax).
            BigDecimal feesSales, BigDecimal discounts,
            // Payments received in the period, net of refunds.
            BigDecimal collected) {
    }

    // Work in the shop right now (not limited to the period).
    public record OpenWork(long openCards, long awaitingApproval, long inWork, BigDecimal pendingValue,
            // Issued invoices not fully paid: how many, what is owed, and how much of it is past due.
            long unpaidInvoices, BigDecimal unpaidBalance, BigDecimal overdueBalance) {
    }

    public record SummaryResponse(Instant from, Instant to, PeriodMetrics current,
            Instant previousFrom, Instant previousTo, PeriodMetrics previous, OpenWork open) {
    }

    public record DailyRow(LocalDate day, long carCount, BigDecimal sales, BigDecimal partsSales,
            BigDecimal labourSales) {
    }

    public record DailyResponse(Instant from, Instant to, String zone, List<DailyRow> days) {
    }

    public record TechnicianRow(String mechanic, BigDecimal hours, BigDecimal labourValue, long jobCards,
            BigDecimal hoursOnCompleted) {
    }

    public record AdvisorRow(String advisor, long estimatesSent, BigDecimal presentedValue,
            BigDecimal approvedValue, BigDecimal declinedValue, BigDecimal closeRatio,
            long completedCards, BigDecimal completedSales, BigDecimal averageRepairOrder) {
    }

    public record LocationRow(UUID locationId, long carCount, BigDecimal sales, BigDecimal partsSales,
            BigDecimal labourSales, BigDecimal averageRepairOrder, BigDecimal labourHours) {
    }

    public record DeclinedRow(UUID itemId, UUID jobCardId, UUID locationId, UUID customerUserId,
            String customerName, String registration, String vin, String description,
            BigDecimal estimatedPartsCost, BigDecimal estimatedLabourHours, BigDecimal value,
            String decidedBy, Instant decidedAt, String submittedBy) {
    }

    public record PartRow(UUID id, UUID jobCardId, UUID locationId, String partNumber, String name,
            int quantity, BigDecimal unitPrice, BigDecimal total, String recordedBy, Instant recordedAt,
            String registration, String customerName, String recommendation) {
    }

    // ---- invoices (R2a) --------------------------------------------------------------------

    public record PaymentRow(UUID id, UUID invoiceId, String invoiceNumber, UUID locationId, String customerName,
            String registration, String method, BigDecimal amount, String reference, Instant receivedAt,
            String receivedBy) {
    }

    // Payments of one method in the period (refunds count negative).
    public record MethodTotal(String method, long count, BigDecimal amount) {
    }

    public record PaymentsResponse(Instant from, Instant to, List<PaymentRow> rows, boolean truncated,
            List<MethodTotal> byMethod, BigDecimal total) {
    }

    // An issued invoice still owed; days = days since it was issued.
    public record UnpaidRow(UUID invoiceId, String number, UUID locationId, String customerName,
            String customerEmail, String customerPhone, String registration, Instant issuedAt, Instant dueAt,
            BigDecimal total, BigDecimal balanceDue, int days, boolean overdue) {
    }

    // Owed money by age since issue: 0–30, 31–60, 61–90, over 90 days.
    public record AgeBucket(String label, long count, BigDecimal balance) {
    }

    public record UnpaidResponse(Instant asOf, List<UnpaidRow> rows, boolean truncated, List<AgeBucket> buckets,
            BigDecimal totalBalance, BigDecimal overdueBalance) {
    }

    // Tax collected for one category at one rate (rates are copied onto each invoice).
    public record TaxRow(String category, BigDecimal rate, long invoices, BigDecimal taxableAmount, BigDecimal tax) {
    }

    public record TaxResponse(Instant from, Instant to, List<TaxRow> rows, BigDecimal totalTax) {
    }

    // A list report; truncated = more rows matched than the limit returned.
    public record RowsResponse<T>(Instant from, Instant to, List<T> rows, boolean truncated) {
    }
}
