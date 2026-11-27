import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { appointmentsApi, customersApi, vehiclesApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import { PageHeader } from '@/components/common/PageHeader';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useMyLocations, usePreferredLocation } from '@/features/locations/use-locations';
import { isVehicleFieldError, VehicleForm, type VehicleFormResult } from '@/features/vehicles/VehicleForm';
import { useAction } from '@/lib/use-action';

/**
 * A quick estimate at the desk or on the phone: customer, car (added here, with its VIN decoded),
 * and what they asked for. Its job card opens straight away; the visit is booked once they go ahead.
 */
export function NewEstimatePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [customerId, setCustomerId] = useState(params.get('customerId') ?? '');
  const [search, setSearch] = useState('');
  const [vehicleId, setVehicleId] = useState(params.get('vehicleId') ?? '');
  const [addingVehicle, setAddingVehicle] = useState(false);
  const [request, setRequest] = useState('');
  const [error, setError] = useState<string | null>(null);

  const customers = useQuery({
    queryKey: ['customers', { q: search }],
    queryFn: () => customersApi.list({ q: search, size: 20 }),
    enabled: !customerId,
  });
  const customer = useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => customersApi.get(customerId),
    enabled: !!customerId,
  });
  const vehicles = useQuery({
    queryKey: ['vehicles', { customerId }],
    queryFn: () => vehiclesApi.list({ customerId, size: 100 }),
    enabled: !!customerId,
  });
  const vehicleList = vehicles.data?.content ?? [];
  // Several locations: where the car will be looked at. Preselected like the booking page.
  const mine = useMyLocations();
  const preferred = usePreferredLocation();
  const [locationId, setLocationId] = useState('');
  const chosen = mine.find((l) => l.id === locationId) ?? preferred;

  const addVehicle = useAction((values: VehicleFormResult) => vehiclesApi.create({ ...values, customerId }), {
    success: 'Vehicle added',
    invalidate: [['vehicles']],
    onSuccess: (vehicle) => {
      setVehicleId(vehicle.id);
      setAddingVehicle(false);
    },
    onError: isVehicleFieldError,
  });
  const create = useAction(appointmentsApi.createEstimate, {
    success: (a) => (a.jobCardId ? 'Estimate started' : 'Estimate started. Its job card will open shortly.'),
    invalidate: [['appointments'], ['job-cards']],
    onSuccess: (a) => navigate(a.jobCardId ? `/job-cards/${a.jobCardId}` : '/job-cards?estimates=1'),
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : errorMessage(e));
      return true;
    },
  });

  const chooseCustomer = (id: string) => {
    setCustomerId(id);
    setVehicleId('');
    setAddingVehicle(false);
  };
  const trimmed = request.trim();

  return (
    <>
      <PageHeader
        title="New estimate"
        description="For a customer on the phone or at the desk: price the work before booking the visit."
      />
      <Card className="max-w-2xl">
        <CardContent className="grid gap-5 pt-6">
          {!customerId ? (
            <div className="grid gap-2">
              <Label htmlFor="estimate-customer-search">Customer</Label>
              <Input
                id="estimate-customer-search"
                placeholder="Search by name, email or phone"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              <ul className="grid gap-1">
                {(customers.data?.content ?? []).map((c) => (
                  <li key={c.id}>
                    <Button
                      variant="ghost"
                      className="w-full justify-start"
                      onClick={() => chooseCustomer(c.id)}
                    >
                      {c.name} <span className="text-muted-foreground">· {c.email}</span>
                    </Button>
                  </li>
                ))}
              </ul>
              <p className="text-sm text-muted-foreground">
                New customer? Add them on the Customers page first, then come back.
              </p>
            </div>
          ) : (
            <p className="text-sm">
              For <span className="font-medium">{customer.data?.name ?? 'the selected customer'}</span>.{' '}
              <Button variant="link" className="h-auto p-0" onClick={() => chooseCustomer('')}>
                Change customer
              </Button>
            </p>
          )}

          {customerId && (
            <>
              {mine.length > 1 && (
                <Field label="Location">
                  <Select
                    name="locationId"
                    value={chosen?.id ?? ''}
                    onChange={(e) => setLocationId(e.target.value)}
                  >
                    {mine.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.name}
                      </option>
                    ))}
                  </Select>
                </Field>
              )}
              {addingVehicle ? (
                <div className="grid gap-3 rounded-lg border p-4">
                  <p className="text-sm font-medium">Add the car (type the VIN and its details fill in)</p>
                  <VehicleForm
                    submitLabel="Add vehicle"
                    onSubmit={(values) => addVehicle.mutateAsync(values)}
                  />
                  <Button
                    variant="ghost"
                    className="justify-self-start"
                    onClick={() => setAddingVehicle(false)}
                  >
                    Cancel
                  </Button>
                </div>
              ) : (
                <Field
                  label="Vehicle"
                  hint={
                    <Button variant="link" className="h-auto p-0" onClick={() => setAddingVehicle(true)}>
                      Add a vehicle
                    </Button>
                  }
                >
                  <Select value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
                    <option value="">
                      {!vehicles.isPending && vehicleList.length === 0
                        ? 'No vehicles yet'
                        : 'Select a vehicle…'}
                    </option>
                    {vehicleList.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.registration || 'No plate'} — {v.year || ''} {v.make} {v.model}
                      </option>
                    ))}
                  </Select>
                </Field>
              )}
              <Field
                label="What the customer is asking for"
                hint="Becomes the job card's first finding, e.g. 'Brakes squeal when stopping; quote front pads'."
              >
                <Textarea
                  rows={3}
                  maxLength={500}
                  value={request}
                  onChange={(e) => setRequest(e.target.value)}
                />
              </Field>
              {error && <Alert>{error}</Alert>}
              <div>
                <Button
                  disabled={!vehicleId || !trimmed || addingVehicle || create.isPending}
                  onClick={() => {
                    setError(null);
                    create
                      .mutateAsync({
                        vehicleId,
                        locationId: mine.length > 1 ? chosen?.id : undefined,
                        request: trimmed,
                      })
                      .catch(() => undefined);
                  }}
                >
                  Start estimate
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </>
  );
}
