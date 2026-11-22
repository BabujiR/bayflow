import type { InvoiceStatus, PaymentMethod, TaxCategory } from '@/api/types';
import { Badge } from '@/components/ui/badge';

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Draft',
  ISSUED: 'Unpaid',
  PARTIALLY_PAID: 'Part paid',
  PAID: 'Paid',
  VOID: 'Void',
};

const VARIANTS: Record<InvoiceStatus, 'secondary' | 'warning' | 'default' | 'success' | 'outline'> = {
  DRAFT: 'secondary',
  ISSUED: 'warning',
  PARTIALLY_PAID: 'default',
  PAID: 'success',
  VOID: 'outline',
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  return <Badge variant={VARIANTS[status]}>{INVOICE_STATUS_LABELS[status]}</Badge>;
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  CARD: 'Card',
  CHECK: 'Check',
  BANK_TRANSFER: 'Bank transfer',
  OTHER: 'Other',
};

export const TAX_LABELS: Record<TaxCategory, string> = {
  PARTS: 'parts',
  LABOUR: 'labour',
  FEES: 'fees',
};

/** "INV-1001", or "Draft invoice" before it is issued. */
export const invoiceTitle = (invoice: { number: string | null }) => invoice.number ?? 'Draft invoice';

/** Up to 3 decimals without trailing zeros: 8.875 → "8.875", 5 → "5". */
export const formatRate = (rate: number) =>
  new Intl.NumberFormat(undefined, { maximumFractionDigits: 3 }).format(rate);

/** Quantities: whole numbers plain, hours with up to 2 decimals. */
export const formatQuantity = (quantity: number) =>
  new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(quantity);
