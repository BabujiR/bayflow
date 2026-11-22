package com.bayflow.workshop.reports;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.bayflow.workshop.application.ApiException;

/** A half-open period [from, to), checked once at the API edge. */
public record ReportPeriod(Instant from, Instant to) {

    /** A year and a day, so "last 12 months" and a leap year still fit. */
    static final Duration MAX_LENGTH = Duration.ofDays(366);

    public static ReportPeriod of(Instant from, Instant to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("invalid-period", "Both from and to are required.");
        }
        if (!from.isBefore(to)) {
            throw ApiException.badRequest("invalid-period", "from must be before to.");
        }
        if (Duration.between(from, to).compareTo(MAX_LENGTH.plusHours(1)) > 0) {
            throw ApiException.badRequest("invalid-period", "A report covers at most 366 days.");
        }
        return new ReportPeriod(from, to);
    }

    /** The period of the same length just before this one, for comparisons. */
    public ReportPeriod previous() {
        Duration length = Duration.between(from, to);
        return new ReportPeriod(from.minus(length), from);
    }

    public static ZoneId zone(String zone) {
        try {
            return ZoneId.of(zone == null || zone.isBlank() ? "UTC" : zone);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("invalid-zone", "Unknown time zone: " + zone);
        }
    }
}
