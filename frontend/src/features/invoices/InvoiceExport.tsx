import { useState } from 'react';

import { invoicesApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { csvFilename, downloadCsv } from '@/features/reports/csv';
import { PeriodPicker } from '@/features/reports/PeriodPicker';
import { type Period, periodFor } from '@/features/reports/period';

import { paymentsCsv, quickbooksInvoicesCsv } from './quickbooks-csv';

type Kind = 'invoices' | 'payments';

/** "Export" on the invoice list (managers and admins): CSV files for QuickBooks and the accountant. */
export function InvoiceExportButton() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        Export
      </Button>
      {open && <ExportDialog onClose={() => setOpen(false)} />}
    </>
  );
}

function ExportDialog({ onClose }: { onClose: () => void }) {
  const [period, setPeriod] = useState<Period>(() => periodFor('lastMonth'));
  const [busy, setBusy] = useState<Kind | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  async function download(kind: Kind) {
    setBusy(kind);
    setProblem(null);
    setDone(null);
    try {
      const data = await invoicesApi.export(period.from, period.to);
      if (kind === 'invoices') {
        downloadCsv(
          csvFilename('quickbooks-invoices', period.firstDay, period.lastDay),
          quickbooksInvoicesCsv(data.invoices),
        );
        setDone(`${data.invoices.length} ${data.invoices.length === 1 ? 'invoice' : 'invoices'} exported.`);
      } else {
        downloadCsv(csvFilename('payments', period.firstDay, period.lastDay), paymentsCsv(data.payments));
        setDone(`${data.payments.length} ${data.payments.length === 1 ? 'payment' : 'payments'} exported.`);
      }
    } catch (error) {
      setProblem(
        error instanceof ApiError && error.isClientError ? error.message : 'The export failed. Try again.',
      );
    } finally {
      setBusy(null);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title="Export invoices"
        description="Issued invoices and payments received in the period, as CSV files. Voided invoices and drafts are left out."
      >
        <div className="grid gap-4">
          <PeriodPicker key={`${period.preset}-${period.firstDay}`} period={period} onChange={setPeriod} />
          <div className="grid gap-2 rounded-md border p-3">
            <p className="text-sm font-medium">Invoices for QuickBooks Online</p>
            <p className="text-sm text-muted-foreground">
              In QuickBooks: Settings → Import data → Invoices. Products/services are matched by name (Parts,
              Labor, Shop fees, Discount, Sales tax). QuickBooks can&apos;t import invoices this way when its
              own sales tax is turned on, and it may refuse discount lines. In those cases, use the QuickBooks
              connection under Settings.
            </p>
            <Button
              className="justify-self-start"
              disabled={busy !== null}
              onClick={() => void download('invoices')}
            >
              {busy === 'invoices' ? 'Exporting…' : 'Download invoices CSV'}
            </Button>
          </div>
          <div className="grid gap-2 rounded-md border p-3">
            <p className="text-sm font-medium">Payments</p>
            <p className="text-sm text-muted-foreground">
              Every payment and refund recorded in the period, with its invoice number and method.
            </p>
            <Button
              variant="outline"
              className="justify-self-start"
              disabled={busy !== null}
              onClick={() => void download('payments')}
            >
              {busy === 'payments' ? 'Exporting…' : 'Download payments CSV'}
            </Button>
          </div>
          {problem && (
            <p role="alert" className="text-sm text-destructive">
              {problem}
            </p>
          )}
          {done && (
            <p role="status" className="text-sm text-muted-foreground">
              {done}
            </p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
