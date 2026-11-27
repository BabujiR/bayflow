import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { auditApi } from '@/api/endpoints';
import type { AuditAction, AuditEvent } from '@/api/types';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDateTime } from '@/lib/format';

/** What each change reads as: "Created Sam's account". */
const ACTION_LABELS: Record<AuditAction, string> = {
  STAFF_CREATED: 'Created the account',
  ROLES_CHANGED: 'Changed roles',
  LOCATIONS_CHANGED: 'Changed locations',
  ACCOUNT_ENABLED: 'Enabled the account',
  ACCOUNT_DISABLED: 'Disabled the account',
  ACCOUNT_UNLOCKED: 'Unlocked the account',
  PASSWORD_RESET: 'Reset the password',
  AVATAR_RESET: 'Removed the photo',
};

export const actionLabel = (action: string) => ACTION_LABELS[action as AuditAction] ?? action;

/** Admin > Activity: who changed which account, and when (newest first). */
export function ActivityPage() {
  const [page, setPage] = useState(0);
  const params = { page, size: 25 };
  const events = useQuery({ queryKey: ['audit-events', params], queryFn: () => auditApi.list(params) });

  return (
    <>
      <PageHeader
        title="Activity"
        description="Changes to your team's and customers' accounts: roles, locations, access, password resets and photos removed, with who made them. Kept for good; nobody can edit or delete it."
      />
      <QueryState query={events}>
        {(result) =>
          result.content.length === 0 ? (
            <EmptyState
              title="Nothing yet"
              description="Changes to accounts show here: a new staff member, new roles, a disabled account."
            />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    <TableHead>Who</TableHead>
                    <TableHead>What</TableHead>
                    <TableHead>Account</TableHead>
                    <TableHead>Details</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.content.map((event: AuditEvent) => (
                    <TableRow key={event.id}>
                      <TableCell className="whitespace-nowrap">{formatDateTime(event.occurredAt)}</TableCell>
                      <TableCell>{event.actorName ?? 'Ramplight'}</TableCell>
                      <TableCell>{actionLabel(event.action)}</TableCell>
                      <TableCell className="font-medium">{event.targetName ?? '—'}</TableCell>
                      <TableCell className="text-muted-foreground">{event.details ?? ''}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />
            </>
          )
        }
      </QueryState>
    </>
  );
}
