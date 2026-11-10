import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';

import { appointmentsApi, customersApi, vehiclesApi } from '@/api/endpoints';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { AppointmentStatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDateTime, formatMileage, SERVICE_TYPE_LABELS } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { isVehicleFieldError, VehicleForm } from '../vehicles/VehicleForm';

export function CustomerDetailPage() {
  const { id = '' } = useParams();
  const { hasRole } = useAuth();
  const advisor = hasRole('SERVICE_ADVISOR');
  const [addingVehicle, setAddingVehicle] = useState(false);
  const customer = useQuery({ queryKey: ['customer', id], queryFn: () => customersApi.get(id) });
  const vehicles = useQuery({
    queryKey: ['vehicles', { customerId: id }],
    queryFn: () => vehiclesApi.list({ customerId: id, size: 100 }),
  });
  const appointments = useQuery({
    queryKey: ['appointments', { customerId: id, size: 50 }],
    queryFn: () => appointmentsApi.list({ customerId: id, size: 50 }),
  });
  const addVehicle = useAction(
    (values: { vin: string; registration: string; make: string; model: string; year: number; mileage: number }) =>
      vehiclesApi.create({ ...values, customerId: id }),
    {
      success: (v) => `${v.make} ${v.model} added`,
      invalidate: [['vehicles']],
      onSuccess: () => setAddingVehicle(false),
      onError: isVehicleFieldError,
    },
  );

  return (
    <QueryState query={customer}>
      {(c) => (
        <div className="grid gap-6">
          <PageHeader
            title={c.name}
            description={[c.email, c.phone].filter(Boolean).join(' · ')}
            actions={
              advisor && (
                <Button asChild>
                  <Link to={`/appointments/new?customerId=${c.id}`}>Book a service</Link>
                </Button>
              )
            }
          />
          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle>Vehicles</CardTitle>
              {advisor && !addingVehicle && (
                <Button size="sm" variant="outline" onClick={() => setAddingVehicle(true)}>
                  Add vehicle
                </Button>
              )}
            </CardHeader>
            <CardContent className="grid gap-4">
              {addingVehicle && (
                <div className="grid gap-3 rounded-lg border p-4">
                  <VehicleForm submitLabel="Add vehicle" onSubmit={(values) => addVehicle.mutateAsync(values)} />
                  <Button variant="ghost" className="justify-self-start" onClick={() => setAddingVehicle(false)}>
                    Cancel
                  </Button>
                </div>
              )}
              <QueryState query={vehicles}>
                {(page) =>
                  page.content.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No vehicles registered.</p>
                  ) : (
                    <ul className="grid gap-2">
                      {page.content.map((v) => (
                        <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                          <Link to={`/vehicles/${v.id}`} className="font-medium text-primary hover:underline">
                            {v.registration} — {v.year} {v.make} {v.model}
                          </Link>
                          <span className="text-muted-foreground">{formatMileage(v.mileage)}</span>
                        </li>
                      ))}
                    </ul>
                  )
                }
              </QueryState>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Appointments</CardTitle>
            </CardHeader>
            <CardContent>
              <QueryState query={appointments}>
                {(page) =>
                  page.content.length === 0 ? (
                    <EmptyState title="No appointments yet" />
                  ) : (
                    <ul className="grid gap-2 text-sm">
                      {page.content.map((a) => (
                        <li key={a.id} className="flex flex-wrap items-center gap-2">
                          <span className="w-44">{formatDateTime(a.scheduledAt)}</span>
                          <span className="font-medium">{a.vehicle.registration}</span>
                          <span className="text-muted-foreground">{SERVICE_TYPE_LABELS[a.serviceType]}</span>
                          <AppointmentStatusBadge status={a.status} />
                          {a.jobCardId && (
                            <Link to={`/job-cards/${a.jobCardId}`} className="text-primary hover:underline">
                              Job card
                            </Link>
                          )}
                        </li>
                      ))}
                    </ul>
                  )
                }
              </QueryState>
            </CardContent>
          </Card>
        </div>
      )}
    </QueryState>
  );
}
