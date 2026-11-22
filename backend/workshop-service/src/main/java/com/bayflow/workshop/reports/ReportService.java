package com.bayflow.workshop.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.workshop.reports.ReportDtos.AdvisorRow;
import com.bayflow.workshop.reports.ReportDtos.DailyResponse;
import com.bayflow.workshop.reports.ReportDtos.DailyRow;
import com.bayflow.workshop.reports.ReportDtos.DeclinedRow;
import com.bayflow.workshop.reports.ReportDtos.LocationRow;
import com.bayflow.workshop.reports.ReportDtos.OpenWork;
import com.bayflow.workshop.reports.ReportDtos.PartRow;
import com.bayflow.workshop.reports.ReportDtos.PeriodMetrics;
import com.bayflow.workshop.reports.ReportDtos.RowsResponse;
import com.bayflow.workshop.reports.ReportDtos.SummaryResponse;
import com.bayflow.workshop.reports.ReportDtos.TechnicianRow;

/**
 * Read-only figures for the owner dashboard and the reports, straight from the job-card
 * tables. Every query runs in a transaction, so row-level security limits it to the caller's
 * workshop and to the locations they may see (narrowed by the location switcher).
 * <p>
 * Sales count COMPLETED cards by completion time. Estimates count recommendations by the time
 * the customer (or advisor) decided them, valued at the card's labour rate.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Most rows a list report (declined jobs, parts) returns. */
    public static final int MAX_ROWS = 1000;

    /** 0.00: money with two decimals. */
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private static final String ITEM_VALUE =
            "(r.estimated_parts_cost + r.estimated_labour_hours * j.labour_rate)";

    @PersistenceContext
    private EntityManager em;

    public SummaryResponse summary(ReportPeriod period) {
        ReportPeriod previous = period.previous();
        return new SummaryResponse(period.from(), period.to(), metrics(period),
                previous.from(), previous.to(), metrics(previous), openWork());
    }

    PeriodMetrics metrics(ReportPeriod period) {
        Object[] sales = single("""
                SELECT count(*), coalesce(sum(j.grand_total), 0), coalesce(sum(j.parts_total), 0),
                       coalesce(sum(j.labour_total), 0), coalesce(sum(h.hours), 0)
                FROM ws_job_cards j
                LEFT JOIN LATERAL (SELECT sum(l.hours) AS hours FROM ws_labour_entries l
                                   WHERE l.job_card_id = j.id) h ON true
                WHERE j.status = 'COMPLETED' AND j.completed_at >= :from AND j.completed_at < :to
                """, period);
        Object[] items = single("""
                SELECT count(*) FILTER (WHERE r.approval_status = 'APPROVED'),
                       count(*) FILTER (WHERE r.approval_status = 'DECLINED'),
                       coalesce(sum(%1$s) FILTER (WHERE r.approval_status = 'APPROVED'), 0),
                       coalesce(sum(%1$s) FILTER (WHERE r.approval_status = 'DECLINED'), 0),
                       coalesce(sum(r.estimated_labour_hours) FILTER (WHERE r.approval_status = 'APPROVED'), 0),
                       coalesce(sum(r.estimated_labour_hours) FILTER (WHERE r.approval_status = 'DECLINED'), 0)
                FROM ws_recommended_items r
                JOIN ws_job_cards j ON j.id = r.job_card_id
                WHERE r.decided_at >= :from AND r.decided_at < :to
                  AND r.approval_status IN ('APPROVED', 'DECLINED')
                """.formatted(ITEM_VALUE), period);

        long cars = count(sales[0]);
        BigDecimal total = money(sales[1]);
        BigDecimal labour = money(sales[3]);
        BigDecimal hours = money(sales[4]);
        BigDecimal approved = money(items[2]);
        BigDecimal declined = money(items[3]);
        return new PeriodMetrics(cars, total, money(sales[2]), labour, divide(total, cars), hours,
                divide(labour, hours), count(items[0]), count(items[1]), approved, declined,
                money(items[4]), money(items[5]), ratio(approved, approved.add(declined)));
    }

    OpenWork openWork() {
        Object[] cards = (Object[]) em.createNativeQuery("""
                SELECT count(*) FILTER (WHERE status NOT IN ('COMPLETED', 'DECLINED')),
                       count(*) FILTER (WHERE status = 'AWAITING_APPROVAL'),
                       count(*) FILTER (WHERE status IN ('APPROVED', 'PARTIALLY_APPROVED', 'IN_PROGRESS'))
                FROM ws_job_cards
                """).getSingleResult();
        Object pending = em.createNativeQuery("""
                SELECT coalesce(sum(%s), 0)
                FROM ws_recommended_items r
                JOIN ws_job_cards j ON j.id = r.job_card_id
                WHERE j.status = 'AWAITING_APPROVAL' AND r.approval_status = 'PENDING'
                """.formatted(ITEM_VALUE)).getSingleResult();
        return new OpenWork(count(cards[0]), count(cards[1]), count(cards[2]), money(pending));
    }

    /** Completed sales per calendar day in the workshop's time zone; days without sales are left out. */
    public DailyResponse daily(ReportPeriod period, ZoneId zone) {
        List<DailyRow> days = rows("""
                SELECT CAST(j.completed_at AT TIME ZONE :zone AS date) AS day, count(*),
                       coalesce(sum(j.grand_total), 0), coalesce(sum(j.parts_total), 0),
                       coalesce(sum(j.labour_total), 0)
                FROM ws_job_cards j
                WHERE j.status = 'COMPLETED' AND j.completed_at >= :from AND j.completed_at < :to
                GROUP BY 1
                ORDER BY 1
                """, period, Map.of("zone", zone.getId()),
                r -> new DailyRow(date(r[0]), count(r[1]), money(r[2]), money(r[3]), money(r[4])));
        return new DailyResponse(period.from(), period.to(), zone.getId(), days);
    }

    /** Hours each mechanic logged in the period, on any card (live, not only completed work). */
    public List<TechnicianRow> technicians(ReportPeriod period) {
        return rows("""
                SELECT l.mechanic, sum(l.hours), sum(l.hours * l.hourly_rate), count(DISTINCT l.job_card_id),
                       coalesce(sum(l.hours) FILTER (WHERE j.status = 'COMPLETED'), 0)
                FROM ws_labour_entries l
                JOIN ws_job_cards j ON j.id = l.job_card_id
                WHERE l.created_at >= :from AND l.created_at < :to
                GROUP BY l.mechanic
                ORDER BY 2 DESC, 1
                """, period, Map.of(),
                r -> new TechnicianRow((String) r[0], money(r[1]), money(r[2]), count(r[3]), money(r[4])));
    }

    /** Estimates each advisor sent in the period, what the customers approved, and what was completed. */
    public List<AdvisorRow> advisors(ReportPeriod period) {
        return rows("""
                WITH cards AS (
                    SELECT j.id, j.submitted_by, j.status, j.grand_total
                    FROM ws_job_cards j
                    WHERE j.submitted_at >= :from AND j.submitted_at < :to
                ), items AS (
                    SELECT r.job_card_id,
                           sum(%1$s) AS presented,
                           sum(%1$s) FILTER (WHERE r.approval_status = 'APPROVED') AS approved,
                           sum(%1$s) FILTER (WHERE r.approval_status = 'DECLINED') AS declined
                    FROM ws_recommended_items r
                    JOIN ws_job_cards j ON j.id = r.job_card_id
                    WHERE r.job_card_id IN (SELECT id FROM cards)
                    GROUP BY r.job_card_id
                )
                SELECT coalesce(c.submitted_by, ''), count(*),
                       coalesce(sum(i.presented), 0), coalesce(sum(i.approved), 0), coalesce(sum(i.declined), 0),
                       count(*) FILTER (WHERE c.status = 'COMPLETED'),
                       coalesce(sum(c.grand_total) FILTER (WHERE c.status = 'COMPLETED'), 0)
                FROM cards c
                LEFT JOIN items i ON i.job_card_id = c.id
                GROUP BY 1
                ORDER BY 2 DESC, 1
                """.formatted(ITEM_VALUE), period, Map.of(), r -> {
                    BigDecimal approved = money(r[3]);
                    BigDecimal declined = money(r[4]);
                    long completed = count(r[5]);
                    BigDecimal sales = money(r[6]);
                    return new AdvisorRow((String) r[0], count(r[1]), money(r[2]), approved, declined,
                            ratio(approved, approved.add(declined)), completed, sales, divide(sales, completed));
                });
    }

    /** Completed sales per location. */
    public List<LocationRow> locations(ReportPeriod period) {
        return rows("""
                SELECT j.location_id, count(*), coalesce(sum(j.grand_total), 0), coalesce(sum(j.parts_total), 0),
                       coalesce(sum(j.labour_total), 0), coalesce(sum(h.hours), 0)
                FROM ws_job_cards j
                LEFT JOIN LATERAL (SELECT sum(l.hours) AS hours FROM ws_labour_entries l
                                   WHERE l.job_card_id = j.id) h ON true
                WHERE j.status = 'COMPLETED' AND j.completed_at >= :from AND j.completed_at < :to
                GROUP BY j.location_id
                ORDER BY 3 DESC
                """, period, Map.of(), r -> {
                    long cars = count(r[1]);
                    BigDecimal sales = money(r[2]);
                    return new LocationRow(uuid(r[0]), cars, sales, money(r[3]), money(r[4]),
                            divide(sales, cars), money(r[5]));
                });
    }

    /** Work customers turned down in the period, newest first: the follow-up list. */
    public RowsResponse<DeclinedRow> declined(ReportPeriod period) {
        List<DeclinedRow> rows = rows("""
                SELECT r.id, j.id, j.location_id, j.customer_user_id, j.customer_name, j.registration, j.vin,
                       r.description, r.estimated_parts_cost, r.estimated_labour_hours, %s,
                       r.decided_by, r.decided_at, j.submitted_by
                FROM ws_recommended_items r
                JOIN ws_job_cards j ON j.id = r.job_card_id
                WHERE r.approval_status = 'DECLINED' AND r.decided_at >= :from AND r.decided_at < :to
                ORDER BY r.decided_at DESC, r.id
                LIMIT %d
                """.formatted(ITEM_VALUE, MAX_ROWS + 1), period, Map.of(),
                r -> new DeclinedRow(uuid(r[0]), uuid(r[1]), uuid(r[2]), uuid(r[3]), (String) r[4],
                        (String) r[5], (String) r[6], (String) r[7], money(r[8]), money(r[9]), money(r[10]),
                        (String) r[11], instant(r[12]), (String) r[13]));
        return limited(period, rows);
    }

    /** Parts fitted in the period, newest first. */
    public RowsResponse<PartRow> parts(ReportPeriod period) {
        List<PartRow> rows = rows("""
                SELECT p.id, p.job_card_id, p.location_id, p.part_number, p.name, p.quantity, p.unit_price,
                       p.quantity * p.unit_price, p.recorded_by, p.created_at, j.registration, j.customer_name,
                       r.description
                FROM ws_part_usages p
                JOIN ws_job_cards j ON j.id = p.job_card_id
                LEFT JOIN ws_recommended_items r ON r.id = p.recommendation_id
                WHERE p.created_at >= :from AND p.created_at < :to
                ORDER BY p.created_at DESC, p.id
                LIMIT %d
                """.formatted(MAX_ROWS + 1), period, Map.of(),
                r -> new PartRow(uuid(r[0]), uuid(r[1]), uuid(r[2]), (String) r[3], (String) r[4],
                        ((Number) r[5]).intValue(), money(r[6]), money(r[7]), (String) r[8], instant(r[9]),
                        (String) r[10], (String) r[11], (String) r[12]));
        return limited(period, rows);
    }

    // ---- helpers ------------------------------------------------------------------------

    private static <T> RowsResponse<T> limited(ReportPeriod period, List<T> rows) {
        boolean truncated = rows.size() > MAX_ROWS;
        return new RowsResponse<>(period.from(), period.to(),
                truncated ? List.copyOf(rows.subList(0, MAX_ROWS)) : rows, truncated);
    }

    private Object[] single(String sql, ReportPeriod period) {
        return (Object[]) bind(em.createNativeQuery(sql), period, Map.of()).getSingleResult();
    }

    private <T> List<T> rows(String sql, ReportPeriod period, Map<String, Object> extra,
            Function<Object[], T> mapper) {
        List<?> result = bind(em.createNativeQuery(sql), period, extra).getResultList();
        List<T> rows = new ArrayList<>(result.size());
        for (Object row : result) {
            rows.add(mapper.apply((Object[]) row));
        }
        return rows;
    }

    private static Query bind(Query query, ReportPeriod period, Map<String, Object> extra) {
        query.setParameter("from", period.from());
        query.setParameter("to", period.to());
        extra.forEach(query::setParameter);
        return query;
    }

    static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    static BigDecimal money(Object value) {
        if (value == null) {
            return ZERO;
        }
        BigDecimal number = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
        return number.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal divide(BigDecimal amount, long count) {
        return count == 0 ? ZERO : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal divide(BigDecimal amount, BigDecimal by) {
        return by.signum() == 0 ? ZERO : amount.divide(by, 2, RoundingMode.HALF_UP);
    }

    /** part ÷ whole to four places (0.8125 = 81.25 %), or null when there is nothing to compare. */
    static BigDecimal ratio(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.divide(whole, 4, RoundingMode.HALF_UP);
    }

    static UUID uuid(Object value) {
        return value == null ? null : value instanceof UUID u ? u : UUID.fromString(value.toString());
    }

    static Instant instant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant i -> i;
            case Timestamp t -> t.toInstant();
            case OffsetDateTime o -> o.toInstant();
            default -> Instant.parse(value.toString());
        };
    }

    static LocalDate date(Object value) {
        return switch (value) {
            case LocalDate d -> d;
            case java.sql.Date d -> d.toLocalDate();
            default -> LocalDate.parse(value.toString());
        };
    }
}
