import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { type AppointmentSort, appointmentsApi } from '@/api/endpoints';
import type { AppointmentStatus } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { AppointmentStatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useLocationLookup } from '@/features/locations/use-locations';
import { APPOINTMENT_STATUS_LABELS, formatDateTime, SERVICE_TYPE_LABELS } from '@/lib/format';

import { AppointmentActions } from './AppointmentActions';

const STATUSES = Object.keys(APPOINTMENT_STATUS_LABELS) as AppointmentStatus[];

export const SORT_LABELS: Record<AppointmentSort, string> = {
  upcoming: 'Upcoming first',
  latest: 'Latest first',
  earliest: 'Earliest first',
  recent: 'Recently booked',
};
const SORTS = Object.keys(SORT_LABELS) as AppointmentSort[];
/** The last order chosen, remembered in this browser. */
const SORT_KEY = 'bayflow-appointments-sort';

const isSort = (value: string | null): value is AppointmentSort =>
  value !== null && (SORTS as string[]).includes(value);

function rememberedSort(): AppointmentSort | null {
  try {
    const saved = localStorage.getItem(SORT_KEY);
    return isSort(saved) ? saved : null;
  } catch {
    return null;
  }
}

function rememberSort(sort: AppointmentSort) {
  try {
    localStorage.setItem(SORT_KEY, sort);
  } catch {
    // Private windows may refuse; the choice still applies to this page.
  }
}

/** Customers see their bookings; advisors and admins see everyone's, with filters. */
export function AppointmentsPage() {
  const { hasRole } = useAuth();
  const staff = hasRole('SERVICE_ADVISOR', 'ADMIN');
  const canBook = hasRole('CUSTOMER', 'SERVICE_ADVISOR');
  // Day, status and order live in the address, so reloading, Back and shared links keep them.
  const [params, setParams] = useSearchParams();
  const date = params.get('day') ?? '';
  const statusParam = params.get('status');
  const status: AppointmentStatus | '' =
    statusParam && (STATUSES as string[]).includes(statusParam) ? (statusParam as AppointmentStatus) : '';
  const sortParam = params.get('sort');
  const chosenSort = isSort(sortParam) ? sortParam : rememberedSort();
  // Unless chosen: one day reads in time order, everything else starts with what's coming up.
  const sort: AppointmentSort = chosenSort ?? (date ? 'earliest' : 'upcoming');
  const [page, setPage] = useState(0);
  const update = (changes: Record<string, string>) => {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const [key, value] of Object.entries(changes)) {
          if (value) {
            next.set(key, value);
          } else {
            next.delete(key);
          }
        }
        return next;
      },
      { replace: true },
    );
    setPage(0);
  };
  // Several locations: each booking shows where it is, and its time in that location's zone.
  const locations = useLocationLookup();
  const filters = { date: date || undefined, status: status || undefined, sort, page, size: 20 };
  const appointments = useQuery({
    queryKey: ['appointments', filters],
    queryFn: () => appointmentsApi.list(filters),
  });

  return (
    <>
      <PageHeader
        title="Appointments"
        description={`${staff ? 'All bookings' : 'Your service bookings'}, ${SORT_LABELS[sort].toLowerCase()}.`}
        actions={
          canBook && (
            <Button asChild>
              <Link to="/appointments/new">Book a service</Link>
            </Button>
          )
        }
      />
      <div className="mb-4 flex flex-wrap items-end gap-4">
        <div className="grid gap-1.5">
          <Label htmlFor="filter-date">Day</Label>
          <Input
            id="filter-date"
            type="date"
            value={date}
            onChange={(e) => update({ day: e.target.value })}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="filter-status">Status</Label>
          <Select
            id="filter-status"
            value={status}
            onChange={(e) => update({ status: e.target.value })}
          >
            <option value="">Any status</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {APPOINTMENT_STATUS_LABELS[s]}
              </option>
            ))}
          </Select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="filter-sort">Sort</Label>
          <Select
            id="filter-sort"
            value={sort}
            onChange={(e) => {
              const next = e.target.value as AppointmentSort;
              rememberSort(next);
              update({ sort: next });
            }}
          >
            {SORTS.map((s) => (
              <option key={s} value={s}>
                {SORT_LABELS[s]}
              </option>
            ))}
          </Select>
        </div>
        {(date || status) && (
          <Button variant="ghost" onClick={() => update({ day: '', status: '' })}>
            Clear filters
          </Button>
        )}
      </div>
      <QueryState query={appointments}>
        {(result) =>
          result.content.length === 0 ? (
            <EmptyState
              title="No appointments"
              description={date || status ? 'Nothing matches these filters.' : 'Bookings will appear here.'}
            />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    {locations.hasSeveral && <TableHead>Location</TableHead>}
                    <TableHead>Vehicle</TableHead>
                    {staff && <TableHead>Customer</TableHead>}
                    <TableHead>Service</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.content.map((a) => (
                    <TableRow key={a.id}>
                      <TableCell className="whitespace-nowrap">
                        {formatDateTime(a.scheduledAt, locations.zoneOf(a.locationId))}
                      </TableCell>
                      {locations.hasSeveral && <TableCell>{locations.nameOf(a.locationId)}</TableCell>}
                      <TableCell>
                        <span className="font-medium">{a.vehicle.registration}</span>
                        <span className="block text-xs text-muted-foreground">
                          {a.vehicle.make} {a.vehicle.model}
                        </span>
                      </TableCell>
                      {staff && (
                        <TableCell>
                          <Link to={`/customers/${a.customerId}`} className="hover:underline">
                            {a.customerName}
                          </Link>
                        </TableCell>
                      )}
                      <TableCell>{SERVICE_TYPE_LABELS[a.serviceType]}</TableCell>
                      <TableCell>
                        <AppointmentStatusBadge status={a.status} />
                      </TableCell>
                      <TableCell>
                        <AppointmentActions appointment={a} />
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
