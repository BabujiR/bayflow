import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';

import { invoicesApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/button';
import { useAction } from '@/lib/use-action';

import { invoiceQueryKey } from './InvoicePage';
import { INVOICES_QUERY_KEY } from './InvoicesPage';
import { INVOICE_STATUS_LABELS, invoiceTitle } from './invoice-format';

/** The job card's invoice, or null when it has none (customers: none until it is issued). */
export function useJobCardInvoice(jobCardId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['invoice', 'job-card', jobCardId],
    queryFn: async () => {
      try {
        return await invoicesApi.forJobCard(jobCardId);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
    enabled,
  });
}

/**
 * On a completed job card: a link to its invoice ("INV-1001 · Paid"), or, for advisors and
 * admins, "Create invoice" when it has none.
 */
export function JobCardInvoiceAction({ jobCardId }: { jobCardId: string }) {
  const { hasRole } = useAuth();
  const staff = hasRole('SERVICE_ADVISOR', 'ADMIN');
  const customer = hasRole('CUSTOMER');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const invoice = useJobCardInvoice(jobCardId, staff || customer);
  const create = useAction(() => invoicesApi.create(jobCardId), {
    success: 'Draft invoice created',
    onSuccess: (created) => {
      queryClient.setQueryData(invoiceQueryKey(created.id), created);
      queryClient.setQueryData(['invoice', 'job-card', jobCardId], created);
      void queryClient.invalidateQueries({ queryKey: [INVOICES_QUERY_KEY] });
      navigate(`/invoices/${created.id}`);
    },
  });

  if (invoice.isPending || invoice.isError) {
    return null;
  }
  if (invoice.data) {
    return (
      <Button asChild variant={invoice.data.status === 'DRAFT' ? 'default' : 'outline'}>
        <Link to={`/invoices/${invoice.data.id}`}>
          {invoiceTitle(invoice.data)} · {INVOICE_STATUS_LABELS[invoice.data.status]}
        </Link>
      </Button>
    );
  }
  return staff ? (
    <Button disabled={create.isPending} onClick={() => create.mutate(undefined)}>
      Create invoice
    </Button>
  ) : null;
}
