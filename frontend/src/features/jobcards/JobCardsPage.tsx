import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { jobCardsApi } from '@/api/endpoints';
import type { JobCardStatus } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { JobCardStatusBadge } from '@/components/common/StatusBadge';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDateTime, formatMileage, JOB_CARD_STATUS_LABELS } from '@/lib/format';

const STATUSES = Object.keys(JOB_CARD_STATUS_LABELS) as JobCardStatus[];

export function JobCardsPage() {
  const { hasRole } = useAuth();
  const customer = hasRole('CUSTOMER');
  const [status, setStatus] = useState<JobCardStatus | ''>('');
  const [page, setPage] = useState(0);
  const params = { status: status || undefined, page, size: 20 };
  const jobCards = useQuery({ queryKey: ['job-cards', params], queryFn: () => jobCardsApi.list(params) });

  return (
    <>
      <PageHeader
        title="Job cards"
        description={customer ? 'Inspections and work on your vehicles, newest first.' : 'Every job card, newest first.'}
      />
      <div className="mb-4 grid max-w-xs gap-1.5">
        <Label htmlFor="job-card-status">Status</Label>
        <Select
          id="job-card-status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as JobCardStatus | '');
            setPage(0);
          }}
        >
          <option value="">Any status</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {JOB_CARD_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
      </div>
      <QueryState query={jobCards}>
        {(result) =>
          result.content.length === 0 ? (
            <EmptyState
              title="No job cards"
              description="A job card opens automatically when a vehicle is checked in."
            />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Vehicle</TableHead>
                    {!customer && <TableHead>Customer</TableHead>}
                    <TableHead>Mileage</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Updated</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.content.map((card) => (
                    <TableRow key={card.id}>
                      <TableCell>
                        <Link to={`/job-cards/${card.id}`} className="font-medium text-primary hover:underline">
                          {card.registration}
                        </Link>
                        <span className="block font-mono text-xs text-muted-foreground">{card.vin}</span>
                      </TableCell>
                      {!customer && <TableCell>{card.customerName}</TableCell>}
                      <TableCell>{formatMileage(card.mileage)}</TableCell>
                      <TableCell>
                        <JobCardStatusBadge status={card.status} />
                      </TableCell>
                      <TableCell className="whitespace-nowrap">{formatDateTime(card.updatedAt)}</TableCell>
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
