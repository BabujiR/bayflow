import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { quickbooksApi } from '@/api/endpoints';
import type { QuickBooksChoice, QuickBooksMapping, QuickBooksStatus } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { formatDateTime } from '@/lib/format';
import { useAction } from '@/lib/use-action';

export const QUICKBOOKS_QUERY_KEY = ['quickbooks'] as const;

/** The QuickBooks connection: everyone on staff reads it (it decides the invoice button). */
export function useQuickBooksStatus(enabled = true) {
  return useQuery({
    queryKey: QUICKBOOKS_QUERY_KEY,
    queryFn: quickbooksApi.status,
    enabled,
    staleTime: 60_000,
  });
}

const RESULTS: Record<string, { text: string; good: boolean }> = {
  connected: { text: 'QuickBooks is connected. Now choose where parts, labour and fees go.', good: true },
  cancelled: { text: 'Connecting to QuickBooks was cancelled.', good: false },
  expired: { text: 'That took too long. Connect again.', good: false },
  failed: { text: 'QuickBooks could not be connected. Try again.', good: false },
};

/** Settings → QuickBooks Online (admins): connect the company and choose its products/services. */
export function QuickBooksSection() {
  const status = useQuickBooksStatus();
  const [params, setParams] = useSearchParams();
  const result = RESULTS[params.get('quickbooks') ?? ''];

  return (
    <section aria-labelledby="quickbooks-heading" className="grid max-w-3xl gap-6">
      <div>
        <h2 id="quickbooks-heading" className="text-xl font-semibold tracking-tight">
          QuickBooks Online
        </h2>
        <p className="text-sm text-muted-foreground">
          Send issued invoices and their payments to your QuickBooks company with one click on the invoice.
        </p>
      </div>
      {result && (
        <div
          role="status"
          className={
            result.good
              ? 'flex items-center justify-between gap-3 rounded-md border border-emerald-600/30 bg-emerald-600/10 px-3 py-2 text-sm'
              : 'flex items-center justify-between gap-3 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm'
          }
        >
          {result.text}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              params.delete('quickbooks');
              setParams(params, { replace: true });
            }}
          >
            Dismiss
          </Button>
        </div>
      )}
      <QueryState query={status}>{(s) => <Connection status={s} />}</QueryState>
    </section>
  );
}

function Connection({ status }: { status: QuickBooksStatus }) {
  const queryClient = useQueryClient();
  const connect = useAction(
    () => quickbooksApi.connect(`${window.location.origin}${window.location.pathname}`),
    {
      onSuccess: ({ authorizeUrl }) => window.location.assign(authorizeUrl),
    },
  );
  const disconnect = useAction(() => quickbooksApi.disconnect(), {
    success: 'QuickBooks disconnected',
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: QUICKBOOKS_QUERY_KEY }),
  });

  if (!status.available) {
    return (
      <Card>
        <CardContent className="pt-6 text-sm text-muted-foreground">
          QuickBooks isn&apos;t set up on this server yet. Your Ramplight administrator adds the Intuit app
          keys; until then you can export invoices as CSV from the Invoices page.
        </CardContent>
      </Card>
    );
  }
  if (!status.connected) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Not connected</CardTitle>
          <CardDescription>
            You&apos;ll sign in to Intuit and choose the company. Ramplight can then create customers,
            invoices and payments there; it never reads your bank details.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={() => connect.mutate(undefined)} disabled={connect.isPending}>
            {connect.isPending ? 'Opening Intuit…' : 'Connect to QuickBooks'}
          </Button>
        </CardContent>
      </Card>
    );
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Connected to {status.companyName ?? 'your company'}</CardTitle>
        <CardDescription>
          {status.environment === 'sandbox' ? 'Sandbox company (for testing). ' : ''}
          Connected by {status.connectedBy} on {formatDateTime(status.connectedAt)}.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        <MappingForm status={status} />
        <div className="flex flex-wrap gap-2 border-t pt-4">
          <Button variant="outline" onClick={() => connect.mutate(undefined)} disabled={connect.isPending}>
            Reconnect
          </Button>
          <Button
            variant="outline"
            onClick={() => disconnect.mutate(undefined)}
            disabled={disconnect.isPending}
          >
            Disconnect
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

type Slot = 'parts' | 'labour' | 'fees';

const SLOTS: { slot: Slot; label: string; hint: string }[] = [
  { slot: 'parts', label: 'Parts', hint: 'e.g. a "Parts" non-inventory item' },
  { slot: 'labour', label: 'Labour', hint: 'e.g. a "Labor" service' },
  { slot: 'fees', label: 'Fees', hint: 'Shop supplies, disposal and other fees' },
];

function MappingForm({ status }: { status: QuickBooksStatus }) {
  const queryClient = useQueryClient();
  const items = useQuery({ queryKey: [...QUICKBOOKS_QUERY_KEY, 'items'], queryFn: quickbooksApi.items });
  const accounts = useQuery({
    queryKey: [...QUICKBOOKS_QUERY_KEY, 'accounts'],
    queryFn: quickbooksApi.accounts,
  });
  const m = status.mapping;
  const [ids, setIds] = useState<Record<Slot | 'deposit', string>>({
    parts: m?.partsItemId ?? '',
    labour: m?.labourItemId ?? '',
    fees: m?.feesItemId ?? '',
    deposit: m?.depositAccountId ?? '',
  });
  const nameIn = (list: QuickBooksChoice[] | undefined, id: string) =>
    list?.find((c) => c.id === id)?.name ?? null;
  const save = useAction(
    () => {
      const mapping: QuickBooksMapping = {
        partsItemId: ids.parts,
        partsItemName: nameIn(items.data, ids.parts),
        labourItemId: ids.labour,
        labourItemName: nameIn(items.data, ids.labour),
        feesItemId: ids.fees,
        feesItemName: nameIn(items.data, ids.fees),
        depositAccountId: ids.deposit || null,
        depositAccountName: ids.deposit ? nameIn(accounts.data, ids.deposit) : null,
      };
      return quickbooksApi.saveMapping(mapping);
    },
    {
      success: 'QuickBooks settings saved',
      onSuccess: (saved) => queryClient.setQueryData(QUICKBOOKS_QUERY_KEY, saved),
    },
  );
  const complete = ids.parts !== '' && ids.labour !== '' && ids.fees !== '';

  return (
    <QueryState query={items}>
      {(list) => (
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (complete) {
              save.mutate(undefined);
            }
          }}
        >
          <div>
            <p className="text-sm font-medium">Where invoice lines go</p>
            <p className="text-sm text-muted-foreground">
              QuickBooks needs a product or service for every line. Discounts go on its discount line and the
              tax Ramplight worked out is kept as it is.
            </p>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            {SLOTS.map(({ slot, label, hint }) => (
              <Field key={slot} label={label} hint={hint}>
                <Select value={ids[slot]} onChange={(e) => setIds({ ...ids, [slot]: e.target.value })}>
                  <option value="">Choose…</option>
                  {list.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </Field>
            ))}
          </div>
          <Field
            label="Deposit payments to"
            hint="Optional. Without one, QuickBooks puts payments in Undeposited Funds."
          >
            <Select value={ids.deposit} onChange={(e) => setIds({ ...ids, deposit: e.target.value })}>
              <option value="">Undeposited Funds (QuickBooks default)</option>
              {(accounts.data ?? []).map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </Select>
          </Field>
          <Button type="submit" className="justify-self-start" disabled={!complete || save.isPending}>
            Save
          </Button>
          {!status.ready && (
            <p className="text-sm text-muted-foreground">
              Invoices can be sent once parts, labour and fees are chosen.
            </p>
          )}
        </form>
      )}
    </QueryState>
  );
}
