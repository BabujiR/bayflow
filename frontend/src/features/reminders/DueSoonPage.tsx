import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { remindersApi } from '@/api/endpoints';
import type { DueVehicle } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useLocationLookup } from '@/features/locations/use-locations';
import { formatDate, formatMileage } from '@/lib/format';
import { useAction } from '@/lib/use-action';

const WINDOWS = [7, 30, 60, 90];

/** Vehicles with maintenance due soon or overdue, from the shop's service intervals. */
export function DueSoonPage() {
  const [days, setDays] = useState(30);
  const due = useQuery({
    queryKey: ['service-due', 'list', days],
    queryFn: () => remindersApi.dueSoon(days),
  });
  return (
    <div className="grid gap-6">
      <PageHeader
        title="Due soon"
        description="Vehicles due for a service by your intervals (whichever comes first: miles or months). Call the customer or book them in."
        actions={
          <Field label="Due within">
            <Select value={String(days)} onChange={(e) => setDays(Number(e.target.value))}>
              {WINDOWS.map((d) => (
                <option key={d} value={d}>
                  {d} days
                </option>
              ))}
            </Select>
          </Field>
        }
      />
      <QueryState query={due}>
        {(vehicles) =>
          vehicles.length === 0 ? (
            <Card>
              <CardContent className="pt-6 text-sm text-muted-foreground">
                Nothing due in the next {days} days. Vehicles appear here once a job that counts as one of
                your service intervals is completed.
              </CardContent>
            </Card>
          ) : (
            <DueTable vehicles={vehicles} />
          )
        }
      </QueryState>
    </div>
  );
}

function DueTable({ vehicles }: { vehicles: DueVehicle[] }) {
  const locations = useLocationLookup();
  const { hasRole } = useAuth();
  const canBook = hasRole('SERVICE_ADVISOR');
  const remind = useAction((vehicleId: string) => remindersApi.remind(vehicleId), {
    success: 'Reminder sent to the customer',
    invalidate: [['service-due', 'list']],
  });
  return (
    <Card>
      <CardContent className="overflow-x-auto pt-6">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Vehicle</TableHead>
              <TableHead>Customer</TableHead>
              {locations.hasSeveral && <TableHead>Location</TableHead>}
              <TableHead>Due</TableHead>
              <TableHead>Reminder</TableHead>
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {vehicles.map((v) => (
              <TableRow key={v.vehicleId}>
                <TableCell className="font-medium">{v.registration}</TableCell>
                <TableCell>{v.customerName}</TableCell>
                {locations.hasSeveral && <TableCell>{locations.nameOf(v.locationId)}</TableCell>}
                <TableCell>
                  <ul className="grid gap-1">
                    {v.services.map((s) => (
                      <li key={s.intervalId} className="flex flex-wrap items-center gap-2">
                        <span>{s.name}</span>
                        {s.overdue ? (
                          <Badge variant="destructive">Overdue</Badge>
                        ) : (
                          <span className="text-muted-foreground">{formatDate(s.dueAt)}</span>
                        )}
                        {s.dueMileage !== null && (
                          <span className="text-muted-foreground">or {formatMileage(s.dueMileage)}</span>
                        )}
                      </li>
                    ))}
                  </ul>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {v.remindedAt ? `Reminded ${formatDate(v.remindedAt)}` : '—'}
                </TableCell>
                <TableCell className="whitespace-nowrap text-right">
                  {v.customerUserId && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={remind.isPending}
                      onClick={() => remind.mutate(v.vehicleId)}
                      aria-label={`Send reminder for ${v.registration}`}
                    >
                      Send reminder
                    </Button>
                  )}{' '}
                  {canBook && (
                    <Link
                      className="text-sm text-primary underline"
                      to="/appointments/new"
                      aria-label={`Book ${v.registration}`}
                    >
                      Book
                    </Link>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
