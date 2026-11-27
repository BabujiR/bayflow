import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { jobCardsApi } from '@/api/endpoints';
import type { JobCardStatus } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { JobCardStatusBadge } from '@/components/common/StatusBadge';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { estimateAge } from '@/features/estimates/EstimateVisitCard';
import { useLocationLookup } from '@/features/locations/use-locations';
import { formatDateTime, formatMileage, JOB_CARD_STATUS_LABELS } from '@/lib/format';

const STATUSES = Object.keys(JOB_CARD_STATUS_LABELS) as JobCardStatus[];

/** Desk view: every card, cars at the shop, or quick estimates whose visit hasn't happened yet. */
type Show = 'all' | 'shop' | 'estimates';

export function JobCardsPage() {
  const { hasRole } = useAuth();
  const customer = hasRole('CUSTOMER');
  const desk = hasRole('SERVICE_ADVISOR', 'ADMIN');
  const locations = useLocationLookup();
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<JobCardStatus | ''>('');
  const [show, setShow] = useState<Show>(desk && searchParams.get('estimates') ? 'estimates' : 'all');
  const [page, setPage] = useState(0);
  const estimates = !desk || show === 'all' ? undefined : show === 'estimates';
  const params = { status: status || undefined, estimates, page, size: 20 };
  const jobCards = useQuery({ queryKey: ['job-cards', params], queryFn: () => jobCardsApi.list(params) });

  return (
    <>
      <PageHeader
        title="Job cards"
        description={
          customer ? 'Inspections and work on your vehicles, newest first.' : 'Every job card, newest first.'
        }
        actions={
          desk && (
            <Button asChild>
              <Link to="/estimates/new">New estimate</Link>
            </Button>
          )
        }
      />
      <div className="mb-4 flex flex-wrap gap-4">
        {desk && (
          <div className="grid w-56 gap-1.5">
            <Label htmlFor="job-card-show">Show</Label>
            <Select
              id="job-card-show"
              value={show}
              onChange={(e) => {
                setShow(e.target.value as Show);
                setPage(0);
              }}
            >
              <option value="all">All job cards</option>
              <option value="shop">Cars at the shop</option>
              <option value="estimates">Estimates (not visited yet)</option>
            </Select>
          </div>
        )}
        <div className="grid w-56 gap-1.5">
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
      </div>
      <QueryState query={jobCards}>
        {(result) =>
          result.content.length === 0 ? (
            <EmptyState
              title={show === 'estimates' ? 'No open estimates' : 'No job cards'}
              description={
                show === 'estimates'
                  ? 'Start one with New estimate when a customer asks for a price.'
                  : 'A job card opens automatically when a vehicle is checked in.'
              }
            />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Vehicle</TableHead>
                    {locations.hasSeveral && <TableHead>Location</TableHead>}
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
                        <Link
                          to={`/job-cards/${card.id}`}
                          className="font-medium text-primary hover:underline"
                        >
                          {card.registration}
                        </Link>
                        <span className="block font-mono text-xs text-muted-foreground">{card.vin}</span>
                      </TableCell>
                      {locations.hasSeveral && <TableCell>{locations.nameOf(card.locationId)}</TableCell>}
                      {!customer && <TableCell>{card.customerName}</TableCell>}
                      <TableCell>{formatMileage(card.mileage)}</TableCell>
                      <TableCell>
                        <span className="flex flex-wrap items-center gap-1.5">
                          <JobCardStatusBadge status={card.status} />
                          {card.arrivedAt === null && (
                            <Badge variant="outline" title="Started without a visit; not checked in yet">
                              Estimate · {estimateAge(card.createdAt)}
                            </Badge>
                          )}
                        </span>
                      </TableCell>
                      <TableCell className="whitespace-nowrap">
                        {formatDateTime(card.updatedAt, locations.zoneOf(card.locationId))}
                      </TableCell>
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
