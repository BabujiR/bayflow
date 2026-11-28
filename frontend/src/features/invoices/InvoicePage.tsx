import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { invoicesApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type {
  Invoice,
  InvoiceLineRequest,
  InvoiceLineType,
  PaymentMethod,
  QuickBooksState,
  TaxCategory,
} from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useLocationLookup } from '@/features/locations/use-locations';
import { useQuickBooksStatus } from '@/features/quickbooks/QuickBooksSection';
import { ReviewRequestLine } from '@/features/reviews/ReviewRequestLine';
import { formatDateTime, formatMoney } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { InvoiceDocument } from './InvoiceDocument';
import { InvoiceTotals } from './InvoiceTotals';
import { INVOICES_QUERY_KEY } from './InvoicesPage';
import { InvoiceStatusBadge, invoiceTitle, PAYMENT_METHOD_LABELS } from './invoice-format';

export const invoiceQueryKey = (id: string) => ['invoice', id] as const;

/** One invoice: the draft editor for staff, otherwise the invoice with payments and actions. */
export function InvoicePage() {
  const { id = '' } = useParams();
  const invoice = useQuery({ queryKey: invoiceQueryKey(id), queryFn: () => invoicesApi.get(id) });
  return <QueryState query={invoice}>{(i) => <InvoiceView invoice={i} />}</QueryState>;
}

/** Puts a saved invoice in the cache and refreshes the lists (and the job card's link). */
function useInvoiceSaved() {
  const queryClient = useQueryClient();
  return (saved: Invoice) => {
    queryClient.setQueryData(invoiceQueryKey(saved.id), saved);
    void queryClient.invalidateQueries({ queryKey: [INVOICES_QUERY_KEY] });
    void queryClient.invalidateQueries({ queryKey: ['invoice', 'job-card', saved.jobCardId] });
  };
}

function InvoiceView({ invoice }: { invoice: Invoice }) {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const staff = hasRole('SERVICE_ADVISOR', 'ADMIN');
  const manager = hasRole('MANAGER', 'ADMIN');
  const saved = useInvoiceSaved();
  const queryClient = useQueryClient();
  const [paying, setPaying] = useState<'payment' | 'refund' | null>(null);
  const [voiding, setVoiding] = useState(false);
  const [emailing, setEmailing] = useState(false);
  // Times shown in the invoice's location's zone.
  const zone = useLocationLookup().zoneOf(invoice.locationId);
  const quickbooks = useQuickBooksStatus(staff);
  const sendToQuickBooks = useAction(() => invoicesApi.sendToQuickBooks(invoice.id), {
    success: (i) =>
      i.quickbooks?.status === 'FAILED'
        ? 'QuickBooks refused it; see the note on the invoice'
        : 'Sent to QuickBooks',
    onSuccess: saved,
  });
  const draft = invoice.status === 'DRAFT';
  const open = invoice.status === 'ISSUED' || invoice.status === 'PARTIALLY_PAID';
  const canSend =
    staff &&
    !draft &&
    quickbooks.data?.ready === true &&
    invoice.quickbooks !== null &&
    invoice.quickbooks.status !== 'SENT' &&
    (invoice.status !== 'VOID' || invoice.quickbooks.invoiceId !== null);

  const issue = useAction(() => invoicesApi.issue(invoice.id, invoice.version), {
    success: (i) => `Invoice ${i.number} issued; the customer can see it now`,
    onSuccess: saved,
  });
  const discard = useAction(() => invoicesApi.discard(invoice.id), {
    success: 'Draft discarded',
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: invoiceQueryKey(invoice.id) });
      void queryClient.invalidateQueries({ queryKey: [INVOICES_QUERY_KEY] });
      void queryClient.invalidateQueries({ queryKey: ['invoice', 'job-card', invoice.jobCardId] });
      navigate(`/job-cards/${invoice.jobCardId}`);
    },
  });

  return (
    <div className="grid gap-6">
      <PageHeader
        title={
          <span className="flex flex-wrap items-center gap-3">
            {invoiceTitle(invoice)} <InvoiceStatusBadge status={invoice.status} />
          </span>
        }
        description={`${invoice.customerName} · ${invoice.registration} · ${formatMoney(invoice.total, invoice.currency)}`}
        actions={
          <>
            {staff && (
              <Button asChild variant="outline">
                <Link to={`/job-cards/${invoice.jobCardId}`}>Job card</Link>
              </Button>
            )}
            {!draft && (
              <Button asChild variant="outline">
                <Link to={`/invoices/${invoice.id}/print`} target="_blank" rel="noopener">
                  Print / Save as PDF
                </Link>
              </Button>
            )}
            {staff && !draft && invoice.status !== 'VOID' && (
              <Button variant="outline" onClick={() => setEmailing(true)}>
                Email invoice
              </Button>
            )}
            {canSend && (
              <Button
                variant="outline"
                onClick={() => sendToQuickBooks.mutate(undefined)}
                disabled={sendToQuickBooks.isPending}
              >
                {sendToQuickBooks.isPending ? 'Sending…' : 'Send to QuickBooks'}
              </Button>
            )}
            {staff && open && <Button onClick={() => setPaying('payment')}>Record payment</Button>}
            {manager && invoice.amountPaid > 0 && (
              <Button variant="outline" onClick={() => setPaying('refund')}>
                Refund
              </Button>
            )}
            {manager && !draft && invoice.status !== 'VOID' && invoice.amountPaid === 0 && (
              <Button variant="outline" onClick={() => setVoiding(true)}>
                Void
              </Button>
            )}
          </>
        }
      />

      {draft && staff ? (
        <DraftEditor
          key={invoice.version}
          invoice={invoice}
          onSaved={saved}
          onIssue={() => issue.mutate(undefined)}
          issuing={issue.isPending}
          onDiscard={() => discard.mutate(undefined)}
          discarding={discard.isPending}
        />
      ) : (
        <>
          {staff && invoice.partsProfit && invoice.partsProfit.partsWithCost > 0 && (
            <p className="text-sm text-muted-foreground" data-testid="parts-profit">
              Profit on parts with costs entered:{' '}
              <span className="font-medium text-foreground">
                {formatMoney(invoice.partsProfit.profit, invoice.currency)}
              </span>{' '}
              ({formatMoney(invoice.partsProfit.partsWithCost, invoice.currency)} sold,{' '}
              {formatMoney(invoice.partsProfit.partsCost, invoice.currency)} cost)
            </p>
          )}
          {staff && quickbooks.data?.connected && invoice.quickbooks && (
            <QuickBooksNote state={invoice.quickbooks} />
          )}
          {staff && invoice.emailedAt && (
            <p className="text-sm text-muted-foreground" data-testid="emailed">
              Emailed to <span className="font-medium text-foreground">{invoice.emailedTo}</span> on{' '}
              {formatDateTime(invoice.emailedAt)}
            </p>
          )}
          {staff && invoice.status === 'PAID' && <ReviewRequestLine invoiceId={invoice.id} zone={zone} />}
          <InvoiceDocument invoice={invoice} />
        </>
      )}

      {paying && (
        <PaymentDialog
          invoice={invoice}
          refund={paying === 'refund'}
          onClose={() => setPaying(null)}
          onSaved={saved}
        />
      )}
      {voiding && <VoidDialog invoice={invoice} onClose={() => setVoiding(false)} onSaved={saved} />}
      {emailing && <EmailDialog invoice={invoice} onClose={() => setEmailing(false)} onSaved={saved} />}
    </div>
  );
}

// ---- the draft --------------------------------------------------------------------------------

interface EditableLine extends InvoiceLineRequest {
  key: string;
  type: InvoiceLineType;
  feeCode: string | null;
  fromJobCard: boolean;
}

const toEditable = (invoice: Invoice): EditableLine[] =>
  invoice.lines.map((l) => ({
    key: l.id,
    id: l.id,
    type: l.type,
    feeCode: l.feeCode,
    fromJobCard: l.fromJobCard,
    description: l.description,
    partNumber: l.partNumber,
    quantity: l.quantity,
    unitPrice: l.unitPrice,
    unitCost: l.unitCost,
    taxable: l.taxable,
    taxCategory: l.taxCategory,
  }));

const LINE_LABELS: Record<InvoiceLineType, string> = {
  PART: 'Part',
  LABOUR: 'Labour',
  FEE: 'Fee',
  DISCOUNT: 'Discount',
};

/** A number field kept as text while typing; NaN while it isn't a number yet. */
const parse = (value: string) => (value.trim() === '' ? Number.NaN : Number(value));

function DraftEditor({
  invoice,
  onSaved,
  onIssue,
  issuing,
  onDiscard,
  discarding,
}: {
  invoice: Invoice;
  onSaved: (invoice: Invoice) => void;
  onIssue: () => void;
  issuing: boolean;
  onDiscard: () => void;
  discarding: boolean;
}) {
  const [lines, setLines] = useState<EditableLine[]>(() => toEditable(invoice));
  const [notes, setNotes] = useState(invoice.notes ?? '');
  const [problem, setProblem] = useState<string | null>(null);
  const initial = JSON.stringify({ lines: toEditable(invoice), notes: invoice.notes ?? '' });
  const dirty = JSON.stringify({ lines, notes }) !== initial;
  const invalid = lines.some(
    (l) =>
      !l.description.trim() ||
      !(l.quantity > 0) ||
      !(l.unitPrice >= 0) ||
      (l.unitCost !== null && l.unitCost !== undefined && !(l.unitCost >= 0)),
  );

  const save = useAction(
    () =>
      invoicesApi.update(invoice.id, {
        version: invoice.version,
        notes: notes.trim() || null,
        lines: lines.map((l) => ({
          id: l.id,
          type: l.type,
          description: l.description.trim(),
          partNumber: l.partNumber || null,
          quantity: l.quantity,
          unitPrice: l.unitPrice,
          unitCost: l.type === 'PART' ? (l.unitCost ?? null) : null,
          taxable: l.taxable,
          taxCategory: l.taxCategory,
        })),
      }),
    {
      success: 'Invoice saved',
      onSuccess: onSaved,
      onError: (error) => {
        if (error instanceof ApiError && error.isClientError) {
          setProblem(error.message);
          return true;
        }
        return false;
      },
    },
  );

  const change = (key: string, patch: Partial<EditableLine>) =>
    setLines((current) => current.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  const add = (type: 'FEE' | 'DISCOUNT') =>
    setLines((current) => [
      ...current,
      {
        key: crypto.randomUUID(),
        type,
        feeCode: null,
        fromJobCard: false,
        description: type === 'FEE' ? '' : 'Discount',
        quantity: 1,
        unitPrice: 0,
        taxable: true,
        taxCategory: type === 'FEE' ? 'FEES' : 'LABOUR',
      },
    ]);

  return (
    <div className="grid gap-6">
      <Alert variant="info">
        This is a draft: only staff can see it. Adjust prices, enter what parts cost you (for profit;
        customers never see it), add fees or a discount, then issue it.
      </Alert>
      <Card>
        <CardHeader>
          <CardTitle>Lines</CardTitle>
          <CardDescription>
            Parts and labour come from the job card and can be repriced but not removed. Shop supplies follow
            the labour total.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm" aria-label="Draft lines">
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <th className="py-2 pr-2 font-semibold">Type</th>
                  <th className="py-2 pr-2 font-semibold">Description</th>
                  <th className="w-20 py-2 pr-2 font-semibold">Qty</th>
                  <th className="w-28 py-2 pr-2 font-semibold">Unit price</th>
                  <th className="w-28 py-2 pr-2 font-semibold">Unit cost</th>
                  <th className="w-20 py-2 pr-2 font-semibold">Taxed</th>
                  <th className="w-24 py-2 pr-2 text-right font-semibold">Amount</th>
                  <th className="w-10 py-2" />
                </tr>
              </thead>
              <tbody>
                {lines.map((l) => {
                  const amount = l.quantity * l.unitPrice * (l.type === 'DISCOUNT' ? -1 : 1);
                  const name = l.description || LINE_LABELS[l.type];
                  return (
                    <tr key={l.key} className="border-b align-top last:border-0" data-testid="draft-line">
                      <td className="py-2 pr-2 text-muted-foreground">{LINE_LABELS[l.type]}</td>
                      <td className="py-2 pr-2">
                        <Input
                          aria-label={`Description of ${name}`}
                          value={l.description}
                          maxLength={200}
                          onChange={(e) => change(l.key, { description: e.target.value })}
                        />
                        {l.type === 'DISCOUNT' && (
                          <Select
                            aria-label={`Discount applies to (${name})`}
                            className="mt-1 h-8 text-xs"
                            value={l.taxCategory}
                            onChange={(e) => change(l.key, { taxCategory: e.target.value as TaxCategory })}
                          >
                            <option value="LABOUR">Off labour</option>
                            <option value="PARTS">Off parts</option>
                          </Select>
                        )}
                      </td>
                      <td className="py-2 pr-2">
                        <NumberInput
                          label={`Quantity of ${name}`}
                          value={l.quantity}
                          onChange={(quantity) => change(l.key, { quantity })}
                        />
                      </td>
                      <td className="py-2 pr-2">
                        <NumberInput
                          label={l.type === 'DISCOUNT' ? `Amount off (${name})` : `Unit price of ${name}`}
                          value={l.unitPrice}
                          disabled={l.feeCode === 'SHOP_SUPPLIES'}
                          onChange={(unitPrice) => change(l.key, { unitPrice })}
                        />
                      </td>
                      <td className="py-2 pr-2">
                        {l.type === 'PART' ? (
                          <NumberInput
                            label={`Unit cost of ${name}`}
                            value={l.unitCost ?? null}
                            optional
                            onChange={(unitCost) =>
                              change(l.key, { unitCost: Number.isNaN(unitCost) ? null : unitCost })
                            }
                          />
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="py-2 pr-2">
                        <input
                          type="checkbox"
                          aria-label={`${name} is taxed`}
                          className="size-4"
                          checked={l.taxable ?? true}
                          onChange={(e) => change(l.key, { taxable: e.target.checked })}
                        />
                      </td>
                      <td className="py-2 pr-2 text-right tabular-nums">
                        {Number.isFinite(amount) ? formatMoney(amount, invoice.currency) : '—'}
                      </td>
                      <td className="py-2">
                        {!l.fromJobCard && (
                          <Button
                            type="button"
                            size="sm"
                            variant="ghost"
                            aria-label={`Remove ${name}`}
                            onClick={() => setLines((current) => current.filter((x) => x.key !== l.key))}
                          >
                            ×
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" variant="outline" onClick={() => add('FEE')}>
              Add fee
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => add('DISCOUNT')}>
              Add discount
            </Button>
          </div>
          <Field
            label="Notes on the invoice"
            hint="Printed on the invoice, e.g. “Next service due at 50,000 mi”."
          >
            <Textarea rows={2} maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          {problem && (
            <p role="alert" className="text-sm text-destructive">
              {problem}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              disabled={!dirty || invalid || save.isPending}
              onClick={() => save.mutate(undefined)}
            >
              Save changes
            </Button>
            {dirty && (
              <span className="text-sm text-muted-foreground">
                Unsaved changes: totals update when saved.
              </span>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Totals</CardTitle>
          <CardDescription>As last saved, with this location's tax rates.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <InvoiceTotals invoice={invoice} />
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              disabled={dirty || issuing}
              onClick={onIssue}
              title={dirty ? 'Save changes first' : undefined}
            >
              Issue invoice
            </Button>
            <Button type="button" variant="ghost" disabled={discarding} onClick={onDiscard}>
              Discard draft
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

/** A money or quantity field; empty = NaN (or null cost when optional). */
function NumberInput({
  label,
  value,
  onChange,
  disabled,
  optional,
}: {
  label: string;
  value: number | null;
  onChange: (value: number) => void;
  disabled?: boolean;
  optional?: boolean;
}) {
  const [text, setText] = useState(value === null || Number.isNaN(value) ? '' : String(value));
  return (
    <Input
      inputMode="decimal"
      aria-label={label}
      disabled={disabled}
      placeholder={optional ? 'optional' : undefined}
      value={text}
      aria-invalid={!optional && Number.isNaN(parse(text)) ? true : undefined}
      onChange={(e) => {
        setText(e.target.value);
        onChange(parse(e.target.value));
      }}
    />
  );
}

// ---- payments and voiding -----------------------------------------------------------------------

function PaymentDialog({
  invoice,
  refund,
  onClose,
  onSaved,
}: {
  invoice: Invoice;
  refund: boolean;
  onClose: () => void;
  onSaved: (invoice: Invoice) => void;
}) {
  const [method, setMethod] = useState<PaymentMethod>('CARD');
  const [amount, setAmount] = useState(String(refund ? invoice.amountPaid : invoice.balanceDue));
  const [reference, setReference] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  // One key per dialog: a double click or a retry records the payment once.
  const [key] = useState(() => crypto.randomUUID());
  const value = parse(amount);
  const limit = refund ? invoice.amountPaid : invoice.balanceDue;
  const valid = value > 0 && value <= limit + 0.0001;

  const pay = useAction(
    () =>
      invoicesApi.pay(
        invoice.id,
        { method, amount: refund ? -value : value, reference: reference.trim() || undefined },
        key,
      ),
    {
      success: (i) =>
        refund ? 'Refund recorded' : i.status === 'PAID' ? `${i.number} is paid in full` : 'Payment recorded',
      onSuccess: (saved) => {
        onSaved(saved);
        onClose();
      },
      onError: (error) => {
        if (error instanceof ApiError && error.isClientError) {
          setProblem(error.message);
          return true;
        }
        return false;
      },
    },
  );

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={refund ? `Refund · ${invoiceTitle(invoice)}` : `Record payment · ${invoiceTitle(invoice)}`}
        description={
          refund
            ? `Up to ${formatMoney(invoice.amountPaid, invoice.currency)} paid can be refunded.`
            : `${formatMoney(invoice.balanceDue, invoice.currency)} still owed.`
        }
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) {
              pay.mutate(undefined);
            }
          }}
        >
          <Field label="Method">
            <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((m) => (
                <option key={m} value={m}>
                  {PAYMENT_METHOD_LABELS[m]}
                </option>
              ))}
            </Select>
          </Field>
          <Field
            label={refund ? 'Amount to refund' : 'Amount'}
            error={
              amount && !valid ? `Enter an amount up to ${formatMoney(limit, invoice.currency)}` : undefined
            }
          >
            <Input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
          <Field label="Reference" hint="Optional: card slip, check number or transfer reference.">
            <Input maxLength={100} value={reference} onChange={(e) => setReference(e.target.value)} />
          </Field>
          {problem && (
            <p role="alert" className="text-sm text-destructive">
              {problem}
            </p>
          )}
          <Button type="submit" disabled={!valid || pay.isPending}>
            {refund ? 'Record refund' : 'Record payment'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Where the invoice stands in QuickBooks. */
function QuickBooksNote({ state }: { state: QuickBooksState }) {
  const payments = `${state.paymentsToSend} ${state.paymentsToSend === 1 ? 'payment' : 'payments'}`;
  const refunds =
    state.refundsByHand > 0
      ? ` Enter ${state.refundsByHand === 1 ? 'the refund' : `the ${state.refundsByHand} refunds`} in QuickBooks yourself.`
      : '';
  let text: string;
  switch (state.status) {
    case 'NOT_SENT':
      text = 'Not sent to QuickBooks yet.';
      break;
    case 'SENT':
      text = `In QuickBooks, sent ${formatDateTime(state.syncedAt)}.${refunds}`;
      break;
    case 'OUT_OF_DATE':
      text = `In QuickBooks, but ${state.paymentsToSend > 0 ? `${payments} not sent yet` : 'the void is not sent yet'}.${refunds}`;
      break;
    default:
      text = `QuickBooks: ${state.error ?? 'the last send failed'}`;
      break;
  }
  return (
    <p
      className={state.status === 'FAILED' ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}
      data-testid="quickbooks-state"
    >
      {text}
    </p>
  );
}

/** E-mails the whole invoice to the customer's address, or to one typed in. */
function EmailDialog({
  invoice,
  onClose,
  onSaved,
}: {
  invoice: Invoice;
  onClose: () => void;
  onSaved: (invoice: Invoice) => void;
}) {
  const [to, setTo] = useState(invoice.customerEmail ?? '');
  const address = to.trim();
  const valid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address);
  const send = useAction(() => invoicesApi.email(invoice.id, address), {
    success: (i) => `${invoiceTitle(i)} emailed to ${i.emailedTo}`,
    onSuccess: (sent) => {
      onSaved(sent);
      onClose();
    },
  });
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={`Email ${invoiceTitle(invoice)}`}
        description={
          invoice.customerUserId
            ? 'The whole invoice goes in the email, with a link to view it in their account.'
            : 'The whole invoice goes in the email.'
        }
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) {
              send.mutate(undefined);
            }
          }}
        >
          <Field label="Send to" error={address && !valid ? 'Enter an email address.' : undefined}>
            <Input
              type="email"
              autoComplete="off"
              maxLength={254}
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </Field>
          {invoice.emailedAt && (
            <p className="text-sm text-muted-foreground">
              Last sent to {invoice.emailedTo} on {formatDateTime(invoice.emailedAt)}.
            </p>
          )}
          <Button type="submit" disabled={!valid || send.isPending}>
            {send.isPending ? 'Sending…' : 'Send email'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function VoidDialog({
  invoice,
  onClose,
  onSaved,
}: {
  invoice: Invoice;
  onClose: () => void;
  onSaved: (invoice: Invoice) => void;
}) {
  const [reason, setReason] = useState('');
  const voidIt = useAction(() => invoicesApi.void(invoice.id, reason.trim()), {
    success: (i) => `${i.number} voided`,
    onSuccess: (saved) => {
      onSaved(saved);
      onClose();
    },
  });
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={`Void ${invoiceTitle(invoice)}?`}
        description="It stays on record, marked void, with its number. The job can then be invoiced again."
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (reason.trim()) {
              voidIt.mutate(undefined);
            }
          }}
        >
          <Field label="Reason">
            <Textarea rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          <Button type="submit" variant="destructive" disabled={!reason.trim() || voidIt.isPending}>
            Void invoice
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
