import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { appointmentsApi } from '@/api/endpoints';
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
import { APPOINTMENT_STATUS_LABELS, formatDateTime, SERVICE_TYPE_LABELS } from '@/lib/format';

import { AppointmentActions } from './AppointmentActions';

const STATUSES = Object.keys(APPOINTMENT_STATUS_LABELS) as AppointmentStatus[];

/** Customers see their bookings; advisors and admins see everyone's, with filters. */
export function AppointmentsPage() {
  const { hasRole } = useAuth();
  const staff = hasRole('SERVICE_ADVISOR', 'ADMIN');
  const canBook = hasRole('CUSTOMER', 'SERVICE_ADVISOR');
  const [date, setDate] = useState('');
  const [status, setStatus] = useState<AppointmentStatus | ''>('');
  const [page, setPage] = useState(0);
  const filters = { date: date || undefined, status: status || undefined, page, size: 20 };
  const appointments = useQuery({
    queryKey: ['appointments', filters],
    queryFn: () => appointmentsApi.list(filters),
  });

  return (
    <>
      <PageHeader
        title="Appointments"
        description={staff ? 'All bookings, oldest first.' : 'Your service bookings.'}
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
            onChange={(e) => {
              setDate(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="filter-status">Status</Label>
          <Select
            id="filter-status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as AppointmentStatus | '');
              setPage(0);
            }}
          >
            <option value="">Any status</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {APPOINTMENT_STATUS_LABELS[s]}
              </option>
            ))}
          </Select>
        </div>
        {(date || status) && (
          <Button
            variant="ghost"
            onClick={() => {
              setDate('');
              setStatus('');
            }}
          >
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
                      <TableCell className="whitespace-nowrap">{formatDateTime(a.scheduledAt)}</TableCell>
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
