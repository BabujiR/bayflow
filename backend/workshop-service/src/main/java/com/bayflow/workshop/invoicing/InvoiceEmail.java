package com.bayflow.workshop.invoicing;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * The invoice as an e-mail: the whole invoice in the body (plain text and HTML, no attachment),
 * with a "View online" link for customers who have an account. Inline styles only, and a
 * single-column table layout, so it reads the same in Gmail, Outlook and phone mail apps.
 */
public final class InvoiceEmail {

    /** Who sends it and where the link goes. viewUrl null = no link (customer has no account). */
    public record Letterhead(String workshopName, String locationName, String address, String phone, String email,
            ZoneId zone, String viewUrl) {
    }

    public record Rendered(String subject, String text, String html) {
    }

    private static final String INK = "#0f172a";
    private static final String MUTED = "#475569";
    private static final String RULE = "#e2e8f0";
    private static final String ACCENT = "#0f766e";

    private InvoiceEmail() {
    }

    public static Rendered render(Invoice invoice, Letterhead from) {
        DateTimeFormatter date = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(from.zone());
        String number = invoice.getNumber();
        boolean owing = invoice.getBalanceDue().signum() > 0;
        String subject = (invoice.getStatus() == InvoiceStatus.PAID ? "Receipt for invoice " : "Invoice ")
                + number + " from " + from.workshopName();
        return new Rendered(subject, text(invoice, from, date, owing), html(invoice, from, date, owing));
    }

    // ---- plain text ----------------------------------------------------------------------------

    private static String text(Invoice i, Letterhead from, DateTimeFormatter date, boolean owing) {
        StringBuilder out = new StringBuilder();
        out.append("Hello ").append(i.getCustomerName()).append(",\n\n");
        out.append(owing ? "Here is your invoice from " : "Thank you. Here is your paid invoice from ")
                .append(from.workshopName()).append(".\n\n");
        out.append("Invoice ").append(i.getNumber()).append('\n');
        out.append("Date: ").append(when(i.getIssuedAt(), date)).append('\n');
        if (owing && i.getDueAt() != null) {
            out.append("Due: ").append(when(i.getDueAt(), date)).append('\n');
        }
        out.append("Vehicle: ").append(vehicle(i)).append("\n\n");
        for (InvoiceLine line : i.getLines()) {
            out.append(line.getDescription());
            if (line.getQuantity().compareTo(BigDecimal.ONE) != 0) {
                out.append(" (").append(quantity(line.getQuantity())).append(" × ")
                        .append(money(line.getUnitPrice(), i.getCurrency())).append(')');
            }
            out.append("  ").append(money(line.getLineTotal(), i.getCurrency())).append('\n');
        }
        out.append('\n');
        for (String[] row : totals(i)) {
            out.append(row[0]).append(": ").append(row[1]).append('\n');
        }
        if (i.getNotes() != null) {
            out.append('\n').append(i.getNotes()).append('\n');
        }
        if (from.viewUrl() != null) {
            out.append("\nView it online: ").append(from.viewUrl()).append('\n');
        }
        if (i.getFooter() != null) {
            out.append('\n').append(i.getFooter()).append('\n');
        }
        out.append('\n').append(from.workshopName()).append('\n');
        for (String detail : contact(from)) {
            out.append(detail).append('\n');
        }
        return out.toString();
    }

    // ---- HTML ----------------------------------------------------------------------------------

    private static String html(Invoice i, Letterhead from, DateTimeFormatter date, boolean owing) {
        String currency = i.getCurrency();
        StringBuilder lines = new StringBuilder();
        for (InvoiceLine line : i.getLines()) {
            String detail = line.getQuantity().compareTo(BigDecimal.ONE) == 0 ? ""
                    : "<div style=\"color:" + MUTED + ";font-size:13px\">" + esc(quantity(line.getQuantity()))
                            + " × " + esc(money(line.getUnitPrice(), currency)) + "</div>";
            String partNumber = line.getPartNumber() == null ? ""
                    : "<div style=\"color:" + MUTED + ";font-size:13px\">Part " + esc(line.getPartNumber()) + "</div>";
            lines.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid ").append(RULE).append("\">")
                    .append(esc(line.getDescription())).append(partNumber).append(detail)
                    .append("</td><td style=\"padding:10px 0 10px 16px;border-bottom:1px solid ").append(RULE)
                    .append(";text-align:right;white-space:nowrap;vertical-align:top\">")
                    .append(esc(money(line.getLineTotal(), currency))).append("</td></tr>");
        }
        StringBuilder totals = new StringBuilder();
        for (String[] row : totals(i)) {
            boolean strong = row[0].equals("Total") || row[0].equals("Balance due");
            String style = strong ? "font-weight:700;font-size:16px;padding:6px 0" : "color:" + MUTED + ";padding:3px 0";
            totals.append("<tr><td style=\"").append(style).append("\">").append(esc(row[0]))
                    .append("</td><td style=\"").append(style).append(";text-align:right;white-space:nowrap\">")
                    .append(esc(row[1])).append("</td></tr>");
        }
        String due = owing && i.getDueAt() != null
                ? "<br>Due <strong>" + esc(when(i.getDueAt(), date)) + "</strong>" : "";
        String button = from.viewUrl() == null ? ""
                : "<p style=\"margin:24px 0 4px\"><a href=\"" + esc(from.viewUrl()) + "\" style=\"background:" + ACCENT
                        + ";color:#ffffff;padding:11px 20px;border-radius:8px;text-decoration:none;font-weight:600;"
                        + "display:inline-block\">View online</a></p>";
        String notes = i.getNotes() == null ? ""
                : "<p style=\"margin:20px 0 0;white-space:pre-line\">" + esc(i.getNotes()) + "</p>";
        String footer = i.getFooter() == null ? ""
                : "<p style=\"margin:20px 0 0;color:" + MUTED + ";font-size:13px;white-space:pre-line\">"
                        + esc(i.getFooter()) + "</p>";
        String contact = String.join("<br>", contact(from).stream().map(InvoiceEmail::esc).toList());
        String intro = owing ? "Here is your invoice from " : "Thank you. Here is your paid invoice from ";
        return """
                <!doctype html>
                <html><body style="margin:0;background:#f8fafc;font-family:system-ui,-apple-system,'Segoe UI',\
                Roboto,sans-serif;color:%1$s">
                <div style="max-width:600px;margin:0 auto;padding:28px 16px">
                <div style="font-weight:700;font-size:20px">%2$s</div>
                <div style="color:%3$s;font-size:13px;margin:4px 0 20px">%4$s</div>
                <div style="background:#ffffff;border:1px solid %5$s;border-radius:12px;padding:24px;line-height:1.5">
                <p style="margin:0 0 16px">Hello %6$s,<br>%7$s%2$s.</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse">
                <tr><td style="vertical-align:top"><div style="font-size:18px;font-weight:700">Invoice %8$s</div>
                <div style="color:%3$s;font-size:14px">Issued %9$s%10$s</div></td>
                <td style="vertical-align:top;text-align:right;color:%3$s;font-size:14px">%11$s</td></tr>
                </table>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;\
                margin-top:20px;font-size:15px">
                <tr><th align="left" style="padding:0 0 8px;border-bottom:2px solid %5$s;color:%3$s;font-size:12px;\
                text-transform:uppercase;letter-spacing:.04em">Description</th><th align="right" style="padding:0 0 8px;\
                border-bottom:2px solid %5$s;color:%3$s;font-size:12px;text-transform:uppercase;letter-spacing:.04em">\
                Amount</th></tr>
                %12$s
                </table>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;\
                margin-top:12px;font-size:15px">
                %13$s
                </table>
                %14$s%15$s%16$s
                </div>
                <p style="color:%3$s;font-size:13px;margin:20px 0 0">%17$s</p>
                <p style="color:#94a3b8;font-size:12px;margin-top:16px">Sent by Ramplight for %2$s.</p>
                </div>
                </body></html>
                """.formatted(INK, esc(from.workshopName()), MUTED, esc(nullToEmpty(from.locationName())), RULE,
                esc(i.getCustomerName()), intro, esc(i.getNumber()), esc(when(i.getIssuedAt(), date)), due,
                esc(vehicle(i)), lines, totals, notes, button, footer, contact);
    }

    // ---- shared --------------------------------------------------------------------------------

    /** Label and amount rows under the lines, as on the printed invoice. */
    static List<String[]> totals(Invoice i) {
        String c = i.getCurrency();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Subtotal", money(i.subtotal(), c)});
        tax(rows, "Tax on parts", i.getPartsTaxRate(), i.getPartsTax(), c);
        tax(rows, "Tax on labour", i.getLabourTaxRate(), i.getLabourTax(), c);
        tax(rows, "Tax on fees", i.getFeesTaxRate(), i.getFeesTax(), c);
        rows.add(new String[] {"Total", money(i.getTotal(), c)});
        if (i.getAmountPaid().signum() != 0) {
            rows.add(new String[] {"Paid", money(i.getAmountPaid(), c)});
            rows.add(new String[] {"Balance due", money(i.getBalanceDue(), c)});
        }
        return rows;
    }

    private static void tax(List<String[]> rows, String label, BigDecimal rate, BigDecimal tax, String currency) {
        if (tax.signum() != 0) {
            rows.add(new String[] {label + " (" + rate.stripTrailingZeros().toPlainString() + "%)",
                    money(tax, currency)});
        }
    }

    public static String money(BigDecimal amount, String currency) {
        try {
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
            Currency code = Currency.getInstance(currency);
            format.setCurrency(code);
            format.setMinimumFractionDigits(code.getDefaultFractionDigits());
            format.setMaximumFractionDigits(code.getDefaultFractionDigits());
            return format.format(amount);
        } catch (IllegalArgumentException | NullPointerException e) {
            return currency + " " + amount.toPlainString();
        }
    }

    private static String quantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private static String vehicle(Invoice i) {
        StringBuilder text = new StringBuilder();
        if (i.getVehicleDescription() != null) {
            text.append(i.getVehicleDescription()).append(" · ");
        }
        text.append(i.getRegistration());
        if (i.getMileage() > 0) {
            text.append(" · ").append(String.format(Locale.US, "%,d", i.getMileage())).append(" mi");
        }
        return text.toString();
    }

    private static List<String> contact(Letterhead from) {
        List<String> lines = new ArrayList<>();
        for (String value : new String[] {from.address(), from.phone(), from.email()}) {
            if (value != null && !value.isBlank()) {
                lines.add(value.strip());
            }
        }
        return lines;
    }

    private static String when(Instant instant, DateTimeFormatter date) {
        return instant == null ? "" : date.format(instant);
    }

    private static String esc(String value) {
        return value == null ? "" : htmlEscape(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
