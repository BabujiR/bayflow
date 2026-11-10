import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { vehiclesApi } from '@/api/endpoints';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatMileage } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { VehicleForm } from './VehicleForm';

/** A customer's own vehicles, with a form to add one. */
export function VehiclesPage() {
  const [adding, setAdding] = useState(false);
  const vehicles = useQuery({ queryKey: ['vehicles', 'mine'], queryFn: () => vehiclesApi.list({ size: 100 }) });
  const create = useAction(vehiclesApi.create, {
    success: (v) => `${v.make} ${v.model} added`,
    invalidate: [['vehicles']],
    onSuccess: () => setAdding(false),
  });

  return (
    <>
      <PageHeader
        title="My vehicles"
        description="Your vehicles, their mileage and service history."
        actions={
          !adding && (
            <Button onClick={() => setAdding(true)}>Add vehicle</Button>
          )
        }
      />
      {adding && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle>Add a vehicle</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            <VehicleForm submitLabel="Add vehicle" onSubmit={(values) => create.mutateAsync(values)} />
            <Button variant="ghost" className="justify-self-start" onClick={() => setAdding(false)}>
              Cancel
            </Button>
          </CardContent>
        </Card>
      )}
      <QueryState query={vehicles}>
        {(page) =>
          page.content.length === 0 ? (
            <EmptyState
              title="No vehicles yet"
              description="Add your vehicle to book a service."
              action={!adding && <Button onClick={() => setAdding(true)}>Add vehicle</Button>}
            />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {page.content.map((vehicle) => (
                <li key={vehicle.id}>
                  <Link to={`/vehicles/${vehicle.id}`} className="group block h-full">
                    <Card className="h-full transition-colors group-hover:border-primary/50">
                      <CardHeader>
                        <CardTitle>
                          {vehicle.year} {vehicle.make} {vehicle.model}
                        </CardTitle>
                        <CardDescription>
                          {vehicle.registration} · {formatMileage(vehicle.mileage)}
                        </CardDescription>
                      </CardHeader>
                      <CardContent className="font-mono text-xs text-muted-foreground">{vehicle.vin}</CardContent>
                    </Card>
                  </Link>
                </li>
              ))}
            </ul>
          )
        }
      </QueryState>
    </>
  );
}
