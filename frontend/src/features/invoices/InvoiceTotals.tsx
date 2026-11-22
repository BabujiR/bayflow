import type { Invoice } from '@/api/types';
import { formatMoney } from '@/lib/format';

import { formatRate, TAX_LABELS } from './invoice-format';

/** Parts, labour, fees, discount, subtotal, tax per category, total, paid and balance. */
export function InvoiceTotals({ invoice }: { invoice: Invoice }) {
  const money = (amount: number) => formatMoney(amount, invoice.currency);
  return (
    <dl
      className="ml-auto grid w-full max-w-xs grid-cols-[1fr_auto] gap-x-6 gap-y-1 text-sm"
      aria-label="Totals"
    >
      <dt>Parts</dt>
      <dd className="text-right tabular-nums">{money(invoice.partsTotal)}</dd>
      <dt>Labour</dt>
      <dd className="text-right tabular-nums">{money(invoice.labourTotal)}</dd>
      {invoice.feesTotal > 0 && (
        <>
          <dt>Fees</dt>
          <dd className="text-right tabular-nums">{money(invoice.feesTotal)}</dd>
        </>
      )}
      {invoice.discountTotal > 0 && (
        <>
          <dt>Discount</dt>
          <dd className="text-right tabular-nums">−{money(invoice.discountTotal)}</dd>
        </>
      )}
      <dt className="border-t pt-1 font-medium">Subtotal</dt>
      <dd className="border-t pt-1 text-right font-medium tabular-nums">{money(invoice.subtotal)}</dd>
      {invoice.taxes
        .filter((t) => t.tax > 0)
        .map((t) => (
          <div key={t.category} className="contents">
            <dt>
              Tax on {TAX_LABELS[t.category]} ({formatRate(t.rate)} %)
            </dt>
            <dd className="text-right tabular-nums">{money(t.tax)}</dd>
          </div>
        ))}
      <dt className="border-t pt-1 text-base font-semibold">Total</dt>
      <dd
        className="border-t pt-1 text-right text-base font-semibold tabular-nums"
        data-testid="invoice-total"
      >
        {money(invoice.total)}
      </dd>
      {invoice.amountPaid !== 0 && (
        <>
          <dt>Paid</dt>
          <dd className="text-right tabular-nums">−{money(invoice.amountPaid)}</dd>
        </>
      )}
      {invoice.status !== 'DRAFT' && invoice.status !== 'VOID' && (
        <>
          <dt className="font-semibold">Balance due</dt>
          <dd className="text-right font-semibold tabular-nums" data-testid="invoice-balance">
            {money(invoice.balanceDue)}
          </dd>
        </>
      )}
    </dl>
  );
}
