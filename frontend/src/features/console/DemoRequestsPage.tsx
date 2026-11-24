import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { consoleApi } from '@/api/endpoints';
import type { ConsoleDemoRequest, DemoStatus } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { LOCATION_COUNTS, SHOP_TYPES } from '@/features/site/DemoRequestForm';
import { formatDateTime } from '@/lib/format';
import { useAction } from '@/lib/use-action';

export const DEMO_STATUS_LABELS: Record<DemoStatus, string> = {
  NEW: 'New',
  CONTACTED: 'Contacted',
  DEMO_SCHEDULED: 'Demo scheduled',
  DEMO_DONE: 'Demo done',
  INVITED: 'Invited',
  ONBOARDED: 'Onboarded',
  DECLINED: 'Declined',
};

/** Statuses the team sets by hand (Invited and Onboarded come from setup invites). */
const MANUAL: DemoStatus[] = ['NEW', 'CONTACTED', 'DEMO_SCHEDULED', 'DEMO_DONE', 'DECLINED'];

const VARIANT: Record<
  DemoStatus,
  'default' | 'secondary' | 'success' | 'warning' | 'destructive' | 'outline'
> = {
  NEW: 'warning',
  CONTACTED: 'default',
  DEMO_SCHEDULED: 'default',
  DEMO_DONE: 'secondary',
  INVITED: 'secondary',
  ONBOARDED: 'success',
  DECLINED: 'outline',
};

const KEY = ['console', 'demo-requests'] as const;

/** Console → Demo requests: who asked, and where each request stands. */
export function DemoRequestsPage() {
  const [status, setStatus] = useState<DemoStatus | ''>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<ConsoleDemoRequest | null>(null);
  const list = useQuery({
    queryKey: [...KEY, status, q.trim(), page],
    queryFn: () =>
      consoleApi.demoRequests({ status: status || undefined, q: q.trim() || undefined, page, size: 25 }),
  });
  return (
    <div className="grid gap-6">
      <PageHeader title="Demo requests" description="From Request a demo on the public site, newest first." />
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Status">
          <Select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as DemoStatus | '');
              setPage(0);
            }}
          >
            <option value="">All</option>
            {Object.entries(DEMO_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <div className="w-full sm:w-72">
          <Field label="Search">
            <Input
              placeholder="Name, email, phone or shop"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                setPage(0);
              }}
            />
          </Field>
        </div>
      </div>
      <QueryState query={list}>
        {(data) =>
          data.content.length === 0 ? (
            <Card>
              <CardContent className="pt-6 text-sm text-muted-foreground">
                No demo requests here yet.
              </CardContent>
            </Card>
          ) : (
            <>
              <Card>
                <CardContent className="overflow-x-auto pt-6">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Received</TableHead>
                        <TableHead>Shop</TableHead>
                        <TableHead>Contact</TableHead>
                        <TableHead>Locations</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>
                          <span className="sr-only">Open</span>
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.content.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell className="whitespace-nowrap text-sm">
                            {formatDateTime(r.createdAt)}
                          </TableCell>
                          <TableCell>
                            <div className="font-medium">{r.shopName}</div>
                            <div className="text-xs text-muted-foreground">
                              {SHOP_TYPES[r.shopType] ?? r.shopType}
                              {r.city ? ` · ${r.city}` : ''}
                            </div>
                          </TableCell>
                          <TableCell className="text-sm">
                            <div>
                              {r.firstName} {r.lastName}
                            </div>
                            <div className="text-muted-foreground">{r.email}</div>
                          </TableCell>
                          <TableCell className="text-sm">
                            {LOCATION_COUNTS[r.locations] ?? r.locations}
                          </TableCell>
                          <TableCell>
                            <Badge variant={VARIANT[r.status]}>{DEMO_STATUS_LABELS[r.status]}</Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setOpen(r)}
                              aria-label={`Open ${r.shopName}`}
                            >
                              Open
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
              <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
            </>
          )
        }
      </QueryState>
      {open && <RequestDialog key={open.id} request={open} onClose={() => setOpen(null)} />}
    </div>
  );
}

function RequestDialog({ request, onClose }: { request: ConsoleDemoRequest; onClose: () => void }) {
  const [status, setStatus] = useState<DemoStatus>(request.status);
  const [notes, setNotes] = useState(request.notes ?? '');
  const choices = MANUAL.includes(request.status) ? MANUAL : [request.status, ...MANUAL];
  const save = useAction(
    () =>
      consoleApi.updateDemoRequest(request.id, {
        status,
        notes: notes.trim() || null,
        version: request.version,
      }),
    {
      success: 'Demo request saved',
      invalidate: [[...KEY]],
      onSuccess: onClose,
    },
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={request.shopName} description={`Received ${formatDateTime(request.createdAt)}`}>
        <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-muted-foreground">Contact</dt>
          <dd>
            {request.firstName} {request.lastName}
          </dd>
          <dt className="text-muted-foreground">Email</dt>
          <dd>
            <a className="text-primary underline" href={`mailto:${request.email}`}>
              {request.email}
            </a>
          </dd>
          <dt className="text-muted-foreground">Phone</dt>
          <dd>
            <a className="text-primary underline" href={`tel:${request.phone}`}>
              {request.phone}
            </a>
          </dd>
          <dt className="text-muted-foreground">Shop type</dt>
          <dd>{SHOP_TYPES[request.shopType] ?? request.shopType}</dd>
          <dt className="text-muted-foreground">Locations</dt>
          <dd>{LOCATION_COUNTS[request.locations] ?? request.locations}</dd>
          <dt className="text-muted-foreground">City</dt>
          <dd>{request.city ?? '—'}</dd>
          <dt className="text-muted-foreground">Message</dt>
          <dd className="whitespace-pre-line">{request.message ?? '—'}</dd>
          {request.updatedBy && (
            <>
              <dt className="text-muted-foreground">Last change</dt>
              <dd>
                {request.updatedBy}, {formatDateTime(request.updatedAt)}
              </dd>
            </>
          )}
        </dl>
        <form
          noValidate
          className="mt-4 grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate(undefined);
          }}
        >
          <Field label="Status" hint="Invited and Onboarded are set by sending a setup invite (next step).">
            <Select value={status} onChange={(e) => setStatus(e.target.value as DemoStatus)}>
              {choices.map((s) => (
                <option key={s} value={s}>
                  {DEMO_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Notes" hint="Only Ramplight's team sees these.">
            <Textarea rows={3} maxLength={2000} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          <Button type="submit" className="justify-self-start" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
