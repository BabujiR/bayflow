import type { ExportInvoice, ExportPayment, InvoiceLineType } from '@/api/types';
import { type CsvColumn, toCsv } from '@/features/reports/csv';
import { getWorkshopZone } from '@/lib/format';

import { PAYMENT_METHOD_LABELS } from './invoice-format';

/**
 * CSV files for the accountant. The invoice file follows QuickBooks Online's invoice import
 * (Settings → Import data → Invoices): one row per line, the invoice's details repeated on each,
 * dates as MM/DD/YYYY. QuickBooks matches "Item(Product/Service)" to its products and services
 * by name. Its import takes no sales tax, so tax is a line of its own ("Sales tax").
 */

/** Product/Service names in QuickBooks for each kind of line. */
export const QUICKBOOKS_ITEMS: Record<InvoiceLineType | 'TAX', string> = {
  PART: 'Parts',
  LABOUR: 'Labor',
  FEE: 'Shop fees',
  DISCOUNT: 'Discount',
  TAX: 'Sales tax',
};

/** MM/DD/YYYY in the workshop's time zone. */
export function quickbooksDate(iso: string | null, zone: string | undefined = getWorkshopZone()): string {
  if (!iso) {
    return '';
  }
  const parts = new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: zone,
  }).formatToParts(new Date(iso));
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('month')}/${get('day')}/${get('year')}`;
}

const terms = (days: number) => (days > 0 ? `Net ${days}` : 'Due on receipt');
/** A number, not text: the CSV guard would put an apostrophe before a negative text value. */
const amount = (value: number) => Math.round(value * 100) / 100;

interface Row {
  invoice: ExportInvoice;
  item: string;
  description: string;
  quantity: number;
  rate: number;
  amount: number;
  taxable: boolean;
}

function rows(invoices: ExportInvoice[]): Row[] {
  const out: Row[] = [];
  for (const invoice of invoices) {
    for (const line of invoice.lines) {
      out.push({
        invoice,
        item: QUICKBOOKS_ITEMS[line.type],
        description: line.partNumber ? `${line.description} (${line.partNumber})` : line.description,
        quantity: line.quantity,
        rate: line.type === 'DISCOUNT' ? -line.unitPrice : line.unitPrice,
        amount: line.lineTotal,
        taxable: line.taxable,
      });
    }
    if (invoice.taxTotal !== 0) {
      out.push({
        invoice,
        item: QUICKBOOKS_ITEMS.TAX,
        description: 'Sales tax',
        quantity: 1,
        rate: invoice.taxTotal,
        amount: invoice.taxTotal,
        taxable: false,
      });
    }
  }
  return out;
}

export function quickbooksInvoicesCsv(invoices: ExportInvoice[], zone?: string): string {
  const columns: CsvColumn<Row>[] = [
    { header: 'InvoiceNo', value: (r) => r.invoice.number },
    { header: 'Customer', value: (r) => r.invoice.customerName },
    { header: 'Email', value: (r) => r.invoice.customerEmail },
    { header: 'InvoiceDate', value: (r) => quickbooksDate(r.invoice.issuedAt, zone) },
    { header: 'DueDate', value: (r) => quickbooksDate(r.invoice.dueAt, zone) },
    { header: 'Terms', value: (r) => terms(r.invoice.paymentTermsDays) },
    {
      header: 'Memo',
      value: (r) => [r.invoice.registration, r.invoice.vehicleDescription].filter(Boolean).join(' · '),
    },
    { header: 'Item(Product/Service)', value: (r) => r.item },
    { header: 'ItemDescription', value: (r) => r.description },
    { header: 'ItemQuantity', value: (r) => r.quantity },
    { header: 'ItemRate', value: (r) => amount(r.rate) },
    { header: 'ItemAmount', value: (r) => amount(r.amount) },
    { header: 'Taxable', value: (r) => (r.taxable ? 'Y' : 'N') },
    { header: 'Currency', value: (r) => r.invoice.currency },
  ];
  return toCsv(columns, rows(invoices));
}

export function paymentsCsv(payments: ExportPayment[], zone?: string): string {
  const columns: CsvColumn<ExportPayment>[] = [
    { header: 'Date', value: (p) => quickbooksDate(p.receivedAt, zone) },
    { header: 'InvoiceNo', value: (p) => p.invoiceNumber },
    { header: 'Customer', value: (p) => p.customerName },
    { header: 'Method', value: (p) => PAYMENT_METHOD_LABELS[p.method] },
    { header: 'Reference', value: (p) => p.reference },
    { header: 'Amount', value: (p) => amount(p.amount) },
    { header: 'Currency', value: (p) => p.currency },
    { header: 'ReceivedBy', value: (p) => p.receivedBy },
  ];
  return toCsv(columns, payments);
}
