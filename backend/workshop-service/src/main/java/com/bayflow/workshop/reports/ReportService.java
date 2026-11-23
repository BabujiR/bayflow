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
import com.bayflow.workshop.reports.ReportDtos.AgeBucket;
import com.bayflow.workshop.reports.ReportDtos.DailyResponse;
import com.bayflow.workshop.reports.ReportDtos.DailyRow;
import com.bayflow.workshop.reports.ReportDtos.DeclinedRow;
import com.bayflow.workshop.reports.ReportDtos.LocationRow;
import com.bayflow.workshop.reports.ReportDtos.MethodTotal;
import com.bayflow.workshop.reports.ReportDtos.OpenWork;
import com.bayflow.workshop.reports.ReportDtos.PartRow;
import com.bayflow.workshop.reports.ReportDtos.PaymentRow;
import com.bayflow.workshop.reports.ReportDtos.PaymentsResponse;
import com.bayflow.workshop.reports.ReportDtos.PeriodMetrics;
import com.bayflow.workshop.reports.ReportDtos.RowsResponse;
import com.bayflow.workshop.reports.ReportDtos.SummaryResponse;
import com.bayflow.workshop.reports.ReportDtos.TaxResponse;
import com.bayflow.workshop.reports.ReportDtos.TaxRow;
import com.bayflow.workshop.reports.ReportDtos.TechnicianRow;
import com.bayflow.workshop.reports.ReportDtos.UnpaidResponse;
import com.bayflow.workshop.reports.ReportDtos.UnpaidRow;

/**
 * Read-only figures for the owner dashboard and the reports, straight from the job-card
 * tables. Every query runs in a transaction, so row-level security limits it to the caller's
 * workshop and to the locations they may see (narrowed by the location switcher).
 * <p>
 * Sales (before tax): issued invoices by issue time, and completed cards that have no issued
 * invoice by completion time, so each job counts once (see {@link #SALES}). Estimates count
 * recommendations by the time the customer (or advisor) decided them, valued at the card's
 * labour rate.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** Most rows a list report (declined jobs, parts) returns. */
    public static final int MAX_ROWS = 1000;

    /** 0.00: money with two decimals. */
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    /** Invoices that count: issued and not voided. */
    private static final String LIVE = "('ISSUED', 'PARTIALLY_PAID', 'PAID')";

    /**
     * The period's sales, one row per job: its issued invoice (parts, labour, fees, discounts and
     * the hours on its labour lines, by issue time), or, while it has none, the completed card
     * (by completion time). A draft or voided invoice leaves the job counted from its card.
     */
    static final String SALES = """
            WITH sales AS (
                SELECT i.location_id, i.issued_at AS at, i.parts_total AS parts, i.labour_total AS labour,
                       i.fees_total AS fees, i.discount_total AS discounts,
                       i.parts_total + i.labour_total + i.fees_total - i.discount_total AS subtotal,
                       coalesce((SELECT sum(l.quantity) FROM {h-schema}ws_invoice_lines l
                                 WHERE l.invoice_id = i.id AND l.type = 'LABOUR'), 0) AS hours
                FROM {h-schema}ws_invoices i
                WHERE i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
                  AND i.issued_at >= :from AND i.issued_at < :to
                UNION ALL
                SELECT j.location_id, j.completed_at, coalesce(j.parts_total, 0), coalesce(j.labour_total, 0),
                       0, 0, coalesce(j.grand_total, 0),
                       coalesce((SELECT sum(l.hours) FROM {h-schema}ws_labour_entries l
                                 WHERE l.job_card_id = j.id), 0)
                FROM {h-schema}ws_job_cards j
                WHERE j.status = 'COMPLETED' AND j.completed_at >= :from AND j.completed_at < :to
                  AND NOT EXISTS (SELECT 1 FROM {h-schema}ws_invoices i
                                  WHERE i.job_card_id = j.id AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID'))
            )
            """;

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
        Object[] sales = single(SALES + """
                SELECT count(*), coalesce(sum(subtotal), 0), coalesce(sum(parts), 0), coalesce(sum(labour), 0),
                       coalesce(sum(hours), 0), coalesce(sum(fees), 0), coalesce(sum(discounts), 0)
                FROM sales
                """, period);
        Object collected = bind(em.createNativeQuery("""
                SELECT coalesce(sum(p.amount), 0) FROM {h-schema}ws_payments p
                WHERE p.received_at >= :from AND p.received_at < :to
                """), period, Map.of()).getSingleResult();
        Object[] items = single("""
                SELECT count(*) FILTER (WHERE r.approval_status = 'APPROVED'),
                       count(*) FILTER (WHERE r.approval_status = 'DECLINED'),
                       coalesce(sum(%1$s) FILTER (WHERE r.approval_status = 'APPROVED'), 0),
                       coalesce(sum(%1$s) FILTER (WHERE r.approval_status = 'DECLINED'), 0),
                       coalesce(sum(r.estimated_labour_hours) FILTER (WHERE r.approval_status = 'APPROVED'), 0),
                       coalesce(sum(r.estimated_labour_hours) FILTER (WHERE r.approval_status = 'DECLINED'), 0)
                FROM {h-schema}ws_recommended_items r
                JOIN {h-schema}ws_job_cards j ON j.id = r.job_card_id
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
                money(items[4]), money(items[5]), ratio(approved, approved.add(declined)), money(sales[5]),
                money(sales[6]), money(collected));
    }

    OpenWork openWork() {
        Object[] cards = (Object[]) em.createNativeQuery("""
                SELECT count(*) FILTER (WHERE status NOT IN ('COMPLETED', 'DECLINED')),
                       count(*) FILTER (WHERE status = 'AWAITING_APPROVAL'),
                       count(*) FILTER (WHERE status IN ('APPROVED', 'PARTIALLY_APPROVED', 'IN_PROGRESS'))
                FROM {h-schema}ws_job_cards
                """).getSingleResult();
        Object pending = em.createNativeQuery("""
                SELECT coalesce(sum(%s), 0)
                FROM {h-schema}ws_recommended_items r
                JOIN {h-schema}ws_job_cards j ON j.id = r.job_card_id
                WHERE j.status = 'AWAITING_APPROVAL' AND r.approval_status = 'PENDING'
                """.formatted(ITEM_VALUE)).getSingleResult();
        Object[] unpaid = (Object[]) em.createNativeQuery("""
                SELECT count(*), coalesce(sum(balance_due), 0),
                       coalesce(sum(balance_due) FILTER (WHERE due_at < now()), 0)
                FROM {h-schema}ws_invoices
                WHERE status IN ('ISSUED', 'PARTIALLY_PAID')
                """).getSingleResult();
        return new OpenWork(count(cards[0]), count(cards[1]), count(cards[2]), money(pending), count(unpaid[0]),
                money(unpaid[1]), money(unpaid[2]));
    }

    /** Sales per calendar day in the workshop's time zone; days without sales are left out. */
    public DailyResponse daily(ReportPeriod period, ZoneId zone) {
        List<DailyRow> days = rows(SALES + """
                SELECT CAST(at AT TIME ZONE :zone AS date) AS day, count(*), coalesce(sum(subtotal), 0),
                       coalesce(sum(parts), 0), coalesce(sum(labour), 0)
                FROM sales
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
                FROM {h-schema}ws_labour_entries l
                JOIN {h-schema}ws_job_cards j ON j.id = l.job_card_id
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
                    FROM {h-schema}ws_job_cards j
                    WHERE j.submitted_at >= :from AND j.submitted_at < :to
                ), items AS (
                    SELECT r.job_card_id,
                           sum(%1$s) AS presented,
                           sum(%1$s) FILTER (WHERE r.approval_status = 'APPROVED') AS approved,
                           sum(%1$s) FILTER (WHERE r.approval_status = 'DECLINED') AS declined
                    FROM {h-schema}ws_recommended_items r
                    JOIN {h-schema}ws_job_cards j ON j.id = r.job_card_id
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

    /** Sales per location. */
    public List<LocationRow> locations(ReportPeriod period) {
        return rows(SALES + """
                SELECT location_id, count(*), coalesce(sum(subtotal), 0), coalesce(sum(parts), 0),
                       coalesce(sum(labour), 0), coalesce(sum(hours), 0)
                FROM sales
                GROUP BY location_id
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
                FROM {h-schema}ws_recommended_items r
                JOIN {h-schema}ws_job_cards j ON j.id = r.job_card_id
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
                FROM {h-schema}ws_part_usages p
                JOIN {h-schema}ws_job_cards j ON j.id = p.job_card_id
                LEFT JOIN {h-schema}ws_recommended_items r ON r.id = p.recommendation_id
                WHERE p.created_at >= :from AND p.created_at < :to
                ORDER BY p.created_at DESC, p.id
                LIMIT %d
                """.formatted(MAX_ROWS + 1), period, Map.of(),
                r -> new PartRow(uuid(r[0]), uuid(r[1]), uuid(r[2]), (String) r[3], (String) r[4],
                        ((Number) r[5]).intValue(), money(r[6]), money(r[7]), (String) r[8], instant(r[9]),
                        (String) r[10], (String) r[11], (String) r[12]));
        return limited(period, rows);
    }

    // ---- invoices (R2a) ----------------------------------------------------------------------

    /** Payments and refunds received in the period, newest first, with totals per method. */
    public PaymentsResponse payments(ReportPeriod period) {
        List<PaymentRow> rows = rows("""
                SELECT p.id, i.id, i.number, p.location_id, i.customer_name, i.registration, p.method, p.amount,
                       p.reference, p.received_at, p.received_by
                FROM {h-schema}ws_payments p
                JOIN {h-schema}ws_invoices i ON i.id = p.invoice_id
                WHERE p.received_at >= :from AND p.received_at < :to
                ORDER BY p.received_at DESC, p.id
                LIMIT %d
                """.formatted(MAX_ROWS + 1), period, Map.of(),
                r -> new PaymentRow(uuid(r[0]), uuid(r[1]), (String) r[2], uuid(r[3]), (String) r[4], (String) r[5],
                        (String) r[6], money(r[7]), (String) r[8], instant(r[9]), (String) r[10]));
        List<MethodTotal> byMethod = rows("""
                SELECT p.method, count(*), coalesce(sum(p.amount), 0)
                FROM {h-schema}ws_payments p
                WHERE p.received_at >= :from AND p.received_at < :to
                GROUP BY p.method
                ORDER BY 3 DESC
                """, period, Map.of(), r -> new MethodTotal((String) r[0], count(r[1]), money(r[2])));
        BigDecimal total = byMethod.stream().map(MethodTotal::amount).reduce(ZERO, BigDecimal::add);
        boolean truncated = rows.size() > MAX_ROWS;
        return new PaymentsResponse(period.from(), period.to(),
                truncated ? List.copyOf(rows.subList(0, MAX_ROWS)) : rows, truncated, byMethod, total);
    }

    /** Issued invoices still owed (now, not for a period), oldest first, with ageing buckets. */
    public UnpaidResponse unpaid() {
        String open = "status IN ('ISSUED', 'PARTIALLY_PAID')";
        List<?> result = em.createNativeQuery("""
                SELECT i.id, i.number, i.location_id, i.customer_name, i.customer_email, i.customer_phone,
                       i.registration, i.issued_at, i.due_at, i.total, i.balance_due,
                       CAST(floor(extract(epoch FROM (now() - i.issued_at)) / 86400) AS integer),
                       i.due_at < now()
                FROM {h-schema}ws_invoices i
                WHERE i.%s
                ORDER BY i.issued_at, i.id
                LIMIT %d
                """.formatted(open, MAX_ROWS + 1)).getResultList();
        List<UnpaidRow> rows = new ArrayList<>();
        for (Object row : result) {
            Object[] r = (Object[]) row;
            rows.add(new UnpaidRow(uuid(r[0]), (String) r[1], uuid(r[2]), (String) r[3], (String) r[4], (String) r[5],
                    (String) r[6], instant(r[7]), instant(r[8]), money(r[9]), money(r[10]), ((Number) r[11]).intValue(),
                    Boolean.TRUE.equals(r[12])));
        }
        long[] counts = new long[4];
        BigDecimal[] balances = { ZERO, ZERO, ZERO, ZERO };
        for (Object row : em.createNativeQuery("""
                SELECT CASE WHEN now() - issued_at <= INTERVAL '30 days' THEN 0
                            WHEN now() - issued_at <= INTERVAL '60 days' THEN 1
                            WHEN now() - issued_at <= INTERVAL '90 days' THEN 2
                            ELSE 3 END, count(*), coalesce(sum(balance_due), 0)
                FROM {h-schema}ws_invoices
                WHERE %s
                GROUP BY 1
                """.formatted(open)).getResultList()) {
            Object[] r = (Object[]) row;
            int bucket = ((Number) r[0]).intValue();
            counts[bucket] = count(r[1]);
            balances[bucket] = money(r[2]);
        }
        String[] labels = { "0–30 days", "31–60 days", "61–90 days", "Over 90 days" };
        List<AgeBucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            buckets.add(new AgeBucket(labels[i], counts[i], balances[i]));
        }
        Object[] totals = (Object[]) em.createNativeQuery("""
                SELECT coalesce(sum(balance_due), 0), coalesce(sum(balance_due) FILTER (WHERE due_at < now()), 0)
                FROM {h-schema}ws_invoices
                WHERE %s
                """.formatted(open)).getSingleResult();
        boolean truncated = rows.size() > MAX_ROWS;
        return new UnpaidResponse(Instant.now(), truncated ? List.copyOf(rows.subList(0, MAX_ROWS)) : rows, truncated,
                buckets, money(totals[0]), money(totals[1]));
    }

    /**
     * Tax on the invoices issued in the period, per category and rate (rates are copied onto each
     * invoice, so a change of rate shows as two rows).
     */
    public TaxResponse tax(ReportPeriod period) {
        String perCategory = """
                SELECT '%1$s' AS category, i.%2$s_tax_rate AS rate, i.%2$s_tax AS tax,
                       greatest(coalesce((SELECT sum(l.line_total) FROM {h-schema}ws_invoice_lines l
                                          WHERE l.invoice_id = i.id AND l.taxable AND l.tax_category = '%1$s'), 0), 0)
                           AS taxable
                FROM {h-schema}ws_invoices i
                WHERE i.status IN %3$s AND i.issued_at >= :from AND i.issued_at < :to
                """;
        String sql = "SELECT c.category, c.rate, count(*), coalesce(sum(c.taxable), 0), coalesce(sum(c.tax), 0) FROM ("
                + perCategory.formatted("PARTS", "parts", LIVE) + " UNION ALL "
                + perCategory.formatted("LABOUR", "labour", LIVE) + " UNION ALL "
                + perCategory.formatted("FEES", "fees", LIVE)
                + ") c WHERE c.taxable > 0 OR c.tax > 0 GROUP BY c.category, c.rate ORDER BY c.category, c.rate";
        List<TaxRow> rows = rows(sql, period, Map.of(), r -> new TaxRow((String) r[0], rate(r[1]), count(r[2]),
                money(r[3]), money(r[4])));
        BigDecimal total = rows.stream().map(TaxRow::tax).reduce(ZERO, BigDecimal::add);
        return new TaxResponse(period.from(), period.to(), rows, total);
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

    /** A tax rate in percent, as stored (3 decimals). */
    static BigDecimal rate(Object value) {
        return value == null ? BigDecimal.ZERO : (value instanceof BigDecimal d ? d : new BigDecimal(value.toString()));
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
