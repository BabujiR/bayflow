import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { consoleApi } from '@/api/endpoints';
import type { ConsoleInvitation, InvitationState, IssuedInvitation } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, formatDateTime } from '@/lib/format';
import { workshopUrl } from '@/lib/site';
import { useAction } from '@/lib/use-action';

import { INVITATIONS_KEY, InviteDialog, IssuedDialog } from './InviteDialogs';

export const INVITATION_STATE_LABELS: Record<InvitationState, string> = {
  PENDING: 'Pending',
  EXPIRED: 'Expired',
  ACCEPTED: 'Set up',
  REVOKED: 'Revoked',
};

const VARIANT: Record<InvitationState, 'default' | 'warning' | 'success' | 'outline'> = {
  PENDING: 'default',
  EXPIRED: 'warning',
  ACCEPTED: 'success',
  REVOKED: 'outline',
};

/** Console → Invitations: setup links sent to workshop owners, and where each stands. */
export function InvitationsPage() {
  const [state, setState] = useState<InvitationState | ''>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [issued, setIssued] = useState<IssuedInvitation | null>(null);
  const list = useQuery({
    queryKey: [...INVITATIONS_KEY, state, q.trim(), page],
    queryFn: () =>
      consoleApi.invitations({ state: state || undefined, q: q.trim() || undefined, page, size: 25 }),
  });
  return (
    <div className="grid gap-6">
      <PageHeader
        title="Invitations"
        description="Setup links for new workshops. Each works once, for 7 days."
        actions={<Button onClick={() => setCreating(true)}>New invite</Button>}
      />
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Status">
          <Select
            value={state}
            onChange={(e) => {
              setState(e.target.value as InvitationState | '');
              setPage(0);
            }}
          >
            <option value="">All</option>
            {Object.entries(INVITATION_STATE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <div className="w-full sm:w-72">
          <Field label="Search">
            <Input
              placeholder="Email, contact or shop"
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
                No invitations here yet.
              </CardContent>
            </Card>
          ) : (
            <>
              <Card>
                <CardContent className="overflow-x-auto pt-6">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Owner</TableHead>
                        <TableHead>Shop</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Sent</TableHead>
                        <TableHead>
                          <span className="sr-only">Actions</span>
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.content.map((invitation) => (
                        <InvitationRow key={invitation.id} invitation={invitation} onIssued={setIssued} />
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
      {creating && (
        <InviteDialog
          draft={{ email: '', contactName: '', shopName: '', demoRequestId: null }}
          onClose={() => setCreating(false)}
          onIssued={(result) => {
            setCreating(false);
            setIssued(result);
          }}
        />
      )}
      {issued && <IssuedDialog issued={issued} onClose={() => setIssued(null)} />}
    </div>
  );
}

function InvitationRow({
  invitation: i,
  onIssued,
}: {
  invitation: ConsoleInvitation;
  onIssued: (issued: IssuedInvitation) => void;
}) {
  const resend = useAction(() => consoleApi.resendInvitation(i.id), {
    invalidate: [[...INVITATIONS_KEY]],
    onSuccess: onIssued,
  });
  const revoke = useAction(() => consoleApi.revokeInvitation(i.id), {
    success: `Invite for ${i.email} revoked`,
    invalidate: [[...INVITATIONS_KEY]],
  });
  const open = i.state === 'PENDING' || i.state === 'EXPIRED';
  return (
    <TableRow>
      <TableCell className="text-sm">
        <div className="font-medium">{i.email}</div>
        {i.contactName && <div className="text-muted-foreground">{i.contactName}</div>}
      </TableCell>
      <TableCell className="text-sm">
        {i.workshopSlug ? (
          <a className="text-primary underline" href={workshopUrl(i.workshopSlug, '/login')}>
            {i.workshopSlug}
          </a>
        ) : (
          (i.shopName ?? '—')
        )}
      </TableCell>
      <TableCell>
        <Badge variant={VARIANT[i.state]}>{INVITATION_STATE_LABELS[i.state]}</Badge>
        <div className="mt-1 text-xs text-muted-foreground">
          {i.state === 'PENDING' && `Until ${formatDate(i.expiresAt)}`}
          {i.state === 'EXPIRED' && `Since ${formatDate(i.expiresAt)}`}
          {i.state === 'ACCEPTED' && formatDate(i.acceptedAt)}
          {i.state === 'REVOKED' && `By ${i.revokedBy ?? '—'}`}
        </div>
      </TableCell>
      <TableCell className="whitespace-nowrap text-sm">
        {formatDateTime(i.lastSentAt)}
        <div className="text-xs text-muted-foreground">
          {i.sentCount > 1 ? `${i.sentCount} times, ` : ''}by {i.createdBy}
        </div>
      </TableCell>
      <TableCell className="whitespace-nowrap text-right">
        {open && (
          <div className="flex justify-end gap-2">
            <Button
              size="sm"
              variant="outline"
              disabled={resend.isPending}
              onClick={() => resend.mutate(undefined)}
              aria-label={`Resend invite to ${i.email}`}
            >
              Resend
            </Button>
            {i.state === 'PENDING' && (
              <Button
                size="sm"
                variant="outline"
                disabled={revoke.isPending}
                onClick={() => revoke.mutate(undefined)}
                aria-label={`Revoke invite to ${i.email}`}
              >
                Revoke
              </Button>
            )}
          </div>
        )}
      </TableCell>
    </TableRow>
  );
}
