import type { Invoice } from '@/api/types';
import { WorkshopLogo } from '@/features/branding/WorkshopLogo';
import { useLocationLookup } from '@/features/locations/use-locations';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { formatDate, formatMileage, formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

import { formatQuantity, INVOICE_STATUS_LABELS, invoiceTitle, PAYMENT_METHOD_LABELS } from './invoice-format';
import { InvoiceTotals } from './InvoiceTotals';

/**
 * The invoice as the customer gets it: shop, bill-to, vehicle, lines, tax, payments and
 * balance. Used on the invoice page and the print view; part costs are never shown here.
 */
export function InvoiceDocument({ invoice, className }: { invoice: Invoice; className?: string }) {
  const workshop = useWorkshop();
  const { get, zoneOf, hasSeveral } = useLocationLookup();
  const location = get(invoice.locationId);
  const zone = zoneOf(invoice.locationId);
  const money = (amount: number) => formatMoney(amount, invoice.currency);
  const shop = workshop.data;
  const contact = [
    location?.address ?? shop?.address,
    location?.phone ?? shop?.phone,
    location?.email ?? shop?.email,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <article
      aria-label={`Invoice ${invoiceTitle(invoice)}`}
      className={cn('grid gap-8 rounded-xl border bg-card p-6 text-card-foreground sm:p-10', className)}
      data-testid="invoice-document"
    >
      <header className="flex flex-wrap items-start justify-between gap-6">
        <div className="grid gap-2">
          <WorkshopLogo size="large" onPaper />
          <div>
            <p className="font-semibold">
              {shop?.displayName}
              {hasSeveral && location ? ` · ${location.name}` : ''}
            </p>
            {contact && <p className="text-sm text-muted-foreground">{contact}</p>}
          </div>
        </div>
        <div className="grid gap-1 text-right">
          <h2 className="text-2xl font-bold tracking-tight">{invoiceTitle(invoice)}</h2>
          <p className="text-sm font-medium" data-testid="invoice-status">
            {INVOICE_STATUS_LABELS[invoice.status]}
          </p>
          {invoice.issuedAt && (
            <p className="text-sm text-muted-foreground">Issued {formatDate(invoice.issuedAt, zone)}</p>
          )}
          {invoice.dueAt && invoice.status !== 'PAID' && invoice.status !== 'VOID' && (
            <p className="text-sm text-muted-foreground">
              {invoice.paymentTermsDays === 0 ? 'Due on receipt' : `Due ${formatDate(invoice.dueAt, zone)}`}
            </p>
          )}
        </div>
      </header>

      <div className="grid gap-6 sm:grid-cols-2">
        <section aria-label="Bill to" className="grid gap-0.5 text-sm">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Bill to</p>
          <p className="font-medium">{invoice.customerName}</p>
          {invoice.customerEmail && <p>{invoice.customerEmail}</p>}
          {invoice.customerPhone && <p>{invoice.customerPhone}</p>}
        </section>
        <section aria-label="Vehicle" className="grid gap-0.5 text-sm sm:text-right">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Vehicle</p>
          <p className="font-medium">
            {invoice.registration}
            {invoice.vehicleDescription ? ` · ${invoice.vehicleDescription}` : ''}
          </p>
          <p>VIN {invoice.vin}</p>
          <p>{formatMileage(invoice.mileage)}</p>
        </section>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm" aria-label="Invoice lines">
          <thead>
            <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground">
              <th className="py-2 pr-4 font-semibold">Description</th>
              <th className="py-2 pr-4 text-right font-semibold">Qty</th>
              <th className="py-2 pr-4 text-right font-semibold">Unit price</th>
              <th className="py-2 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody>
            {invoice.lines.map((line) => (
              <tr key={line.id} className="border-b last:border-0">
                <td className="py-2 pr-4">
                  {line.description}
                  {line.partNumber && (
                    <span className="block text-xs text-muted-foreground">{line.partNumber}</span>
                  )}
                </td>
                <td className="py-2 pr-4 text-right tabular-nums">{formatQuantity(line.quantity)}</td>
                <td className="py-2 pr-4 text-right tabular-nums">{money(line.unitPrice)}</td>
                <td className="py-2 text-right tabular-nums">{money(line.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <InvoiceTotals invoice={invoice} />

      {invoice.payments.length > 0 && (
        <section aria-label="Payments" className="grid gap-2 text-sm">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Payments</p>
          <ul className="grid gap-1">
            {invoice.payments.map((p) => (
              <li key={p.id} className="flex flex-wrap justify-between gap-2">
                <span>
                  {formatDate(p.receivedAt, zone)} ·{' '}
                  {p.amount < 0 ? 'Refund' : PAYMENT_METHOD_LABELS[p.method]}
                  {p.reference ? ` · ${p.reference}` : ''}
                </span>
                <span className="tabular-nums">{money(p.amount)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {invoice.status === 'VOID' && (
        <p className="rounded-md border border-destructive/40 p-3 text-sm text-destructive">
          This invoice was voided{invoice.voidReason ? `: ${invoice.voidReason}` : ''}.
        </p>
      )}
      {(invoice.notes || invoice.footer) && (
        <footer className="grid gap-2 border-t pt-4 text-sm text-muted-foreground">
          {invoice.notes && <p className="whitespace-pre-line">{invoice.notes}</p>}
          {invoice.footer && <p className="whitespace-pre-line">{invoice.footer}</p>}
        </footer>
      )}
    </article>
  );
}
