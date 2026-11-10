import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';

import { vehiclesApi } from '@/api/endpoints';
import { useAuth } from '@/auth/auth-context';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatMileage } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { ServiceHistoryList } from './ServiceHistoryList';
import { VehicleForm } from './VehicleForm';

export function VehicleDetailPage() {
  const { id = '' } = useParams();
  const { hasRole } = useAuth();
  const canEdit = hasRole('CUSTOMER', 'SERVICE_ADVISOR');
  const canBook = hasRole('CUSTOMER', 'SERVICE_ADVISOR');
  const [editing, setEditing] = useState(false);
  const vehicle = useQuery({ queryKey: ['vehicle', id], queryFn: () => vehiclesApi.get(id) });
  const update = useAction(
    ({ registration, make, model, year, mileage }: { registration: string; make: string; model: string; year: number; mileage: number }) =>
      vehiclesApi.update(id, { registration, make, model, year, mileage }),
    { success: 'Vehicle updated', invalidate: [['vehicle', id], ['vehicles']], onSuccess: () => setEditing(false) },
  );

  return (
    <QueryState query={vehicle}>
      {(v) => (
        <div className="grid gap-8">
          <PageHeader
            title={`${v.year} ${v.make} ${v.model}`}
            description={`${v.registration} · ${formatMileage(v.mileage)}`}
            actions={
              <>
                {canBook && (
                  <Button asChild>
                    <Link to={`/appointments/new?vehicleId=${v.id}&customerId=${v.customerId}`}>Book a service</Link>
                  </Button>
                )}
                {canEdit && !editing && (
                  <Button variant="outline" onClick={() => setEditing(true)}>
                    Edit details
                  </Button>
                )}
              </>
            }
          />
          <Card>
            <CardHeader>
              <CardTitle>Details</CardTitle>
            </CardHeader>
            <CardContent>
              {editing ? (
                <div className="grid gap-4">
                  <VehicleForm vehicle={v} submitLabel="Save changes" onSubmit={(values) => update.mutateAsync(values)} />
                  <Button variant="ghost" className="justify-self-start" onClick={() => setEditing(false)}>
                    Cancel
                  </Button>
                </div>
              ) : (
                <dl className="grid gap-4 text-sm sm:grid-cols-3">
                  <div>
                    <dt className="text-muted-foreground">VIN</dt>
                    <dd className="font-mono">{v.vin}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Registration</dt>
                    <dd>{v.registration}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Mileage</dt>
                    <dd>{formatMileage(v.mileage)}</dd>
                  </div>
                </dl>
              )}
            </CardContent>
          </Card>
          <section className="grid gap-4">
            <h2 className="text-lg font-semibold">Service history</h2>
            <ServiceHistoryList vehicleId={v.id} />
          </section>
        </div>
      )}
    </QueryState>
  );
}
