import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { invoicesApi } from '@/api/endpoints';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useLocationLookup } from '@/features/locations/use-locations';
import { formatDate, formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

import { InvoiceStatusBadge, invoiceTitle } from './invoice-format';

export const INVOICES_QUERY_KEY = 'invoices';

const TABS = [
  { status: '', label: 'All' },
  { status: 'DRAFT', label: 'Drafts' },
  { status: 'UNPAID', label: 'Unpaid' },
  { status: 'PAID', label: 'Paid' },
  { status: 'VOID', label: 'Void' },
] as const;

/**
 * Staff: every invoice of their locations, by status, searchable. Customers: their own issued
 * invoices.
 */
export function InvoicesPage() {
  const { hasRole } = useAuth();
  const customer = hasRole('CUSTOMER') && !hasRole('SERVICE_ADVISOR', 'ADMIN');
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const params = { status: status || undefined, q: q.trim() || undefined, page, size: 20 };
  const invoices = useQuery({
    queryKey: [INVOICES_QUERY_KEY, params],
    queryFn: () => invoicesApi.list(params),
    placeholderData: keepPreviousData,
  });
  const { nameOf, hasSeveral, zoneOf } = useLocationLookup();

  return (
    <>
      <PageHeader
        title={customer ? 'My invoices' : 'Invoices'}
        description={
          customer
            ? 'Bills for the work on your vehicles. Open one to view or print it.'
            : 'Create an invoice from a completed job card, then issue it and record payments.'
        }
      />
      {!customer && (
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div role="tablist" aria-label="Invoice status" className="flex flex-wrap gap-1">
            {TABS.map((t) => (
              <button
                key={t.label}
                type="button"
                role="tab"
                aria-selected={status === t.status}
                onClick={() => {
                  setStatus(t.status);
                  setPage(0);
                }}
                className={cn(
                  'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                  status === t.status ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted',
                )}
              >
                {t.label}
              </button>
            ))}
          </div>
          <Input
            type="search"
            aria-label="Search invoices"
            placeholder="Number, customer or registration"
            className="h-9 w-72 max-w-full"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
      )}
      <QueryState query={invoices}>
        {(result) =>
          result.content.length === 0 ? (
            <EmptyState
              title="No invoices"
              description={customer ? 'Invoices appear here once the workshop issues them.' : undefined}
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <Table aria-label="Invoices">
                  <TableHeader>
                    <TableRow>
                      <TableHead>Invoice</TableHead>
                      <TableHead>Date</TableHead>
                      {!customer && <TableHead>Customer</TableHead>}
                      <TableHead>Vehicle</TableHead>
                      {!customer && hasSeveral && <TableHead>Location</TableHead>}
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead className="text-right">Balance</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {result.content.map((i) => (
                      <TableRow key={i.id}>
                        <TableCell>
                          <Link to={`/invoices/${i.id}`} className="font-medium text-primary hover:underline">
                            {invoiceTitle(i)}
                          </Link>
                        </TableCell>
                        <TableCell>{formatDate(i.issuedAt ?? i.createdAt, zoneOf(i.locationId))}</TableCell>
                        {!customer && <TableCell>{i.customerName}</TableCell>}
                        <TableCell>{i.registration}</TableCell>
                        {!customer && hasSeveral && <TableCell>{nameOf(i.locationId)}</TableCell>}
                        <TableCell className="text-right tabular-nums">
                          {formatMoney(i.total, i.currency)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {i.status === 'DRAFT' || i.status === 'VOID'
                            ? '—'
                            : formatMoney(i.balanceDue, i.currency)}
                        </TableCell>
                        <TableCell>
                          <InvoiceStatusBadge status={i.status} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />
            </>
          )
        }
      </QueryState>
    </>
  );
}
