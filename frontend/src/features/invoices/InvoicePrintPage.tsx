import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, useParams } from 'react-router';

import { invoicesApi } from '@/api/endpoints';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';

import { InvoiceDocument } from './InvoiceDocument';
import { invoiceQueryKey } from './InvoicePage';
import { invoiceTitle } from './invoice-format';

/**
 * The invoice alone on the page, for the browser's Print / Save as PDF. Always in light colours
 * (paper is white), whatever the app's theme.
 */
export function InvoicePrintPage() {
  const { id = '' } = useParams();
  const invoice = useQuery({ queryKey: invoiceQueryKey(id), queryFn: () => invoicesApi.get(id) });

  useEffect(() => {
    const root = document.documentElement;
    const wasDark = root.classList.contains('dark');
    root.classList.remove('dark');
    return () => {
      if (wasDark) {
        root.classList.add('dark');
      }
    };
  }, []);

  useEffect(() => {
    if (invoice.data) {
      const previous = document.title;
      // The suggested file name when saving as PDF.
      document.title = invoiceTitle(invoice.data);
      return () => {
        document.title = previous;
      };
    }
  }, [invoice.data]);

  return (
    <div className="min-h-svh bg-white px-4 py-6 text-slate-900 print:p-0">
      <div className="mx-auto grid max-w-3xl gap-4">
        <div className="flex flex-wrap gap-2 print:hidden">
          <Button onClick={() => window.print()}>Print / Save as PDF</Button>
          <Button asChild variant="outline">
            <Link to={`/invoices/${id}`}>Back to the invoice</Link>
          </Button>
        </div>
        <QueryState query={invoice}>
          {(i) => <InvoiceDocument invoice={i} className="print:rounded-none print:border-0 print:p-0" />}
        </QueryState>
      </div>
    </div>
  );
}
