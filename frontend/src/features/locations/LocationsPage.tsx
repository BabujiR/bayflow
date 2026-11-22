import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { locationsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { Location } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { formatMoney, timeZones } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { LocationBillingButton } from './LocationBilling';
import { LOCATIONS_QUERY_KEY, useLocations } from './use-locations';

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(100, 'At most 100 characters'),
  code: z
    .string()
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9][a-z0-9-]{0,18}[a-z0-9]$/, '2-20 lower-case letters, digits and hyphens'),
  phone: z.string().trim().max(30, 'At most 30 characters'),
  email: z.union([z.literal(''), z.email('Enter a valid email address')]),
  address: z.string().trim().max(300, 'At most 300 characters'),
  timeZone: z.string(),
  labourRate: z
    .string()
    .trim()
    .refine(
      (v) => v === '' || (/^\d{1,5}(\.\d{1,2})?$/.test(v) && Number(v) <= 10_000),
      'A rate such as 120.00',
    ),
});

type FormInput = z.input<typeof schema>;
type FormOutput = z.output<typeof schema>;

/** The organization's locations (ADMIN): add, change, archive and reactivate them. */
export function LocationsPage() {
  const locations = useLocations();
  const workshop = useWorkshop();
  const [adding, setAdding] = useState(false);
  // Why an archive was refused (upcoming appointments, last location): shown on the page, since
  // the admin has to act on it.
  const [archiveError, setArchiveError] = useState<string | null>(null);
  const archive = useAction(({ id }: { id: string; name: string }) => locationsApi.archive(id), {
    success: (l) => `${l.name} archived`,
    invalidate: [LOCATIONS_QUERY_KEY],
    onSuccess: () => setArchiveError(null),
    onError: (error) => {
      if (error instanceof ApiError && error.isClientError) {
        setArchiveError(error.message);
        return true;
      }
      return false;
    },
  });
  const reactivate = useAction(locationsApi.reactivate, {
    success: (l) => `${l.name} is active again`,
    invalidate: [LOCATIONS_QUERY_KEY],
  });

  return (
    <>
      <PageHeader
        title="Locations"
        description="Your shops. Each can have its own time zone and labour rate; empty fields use the workshop settings."
        actions={
          <Dialog open={adding} onOpenChange={setAdding}>
            <DialogTrigger asChild>
              <Button>Add location</Button>
            </DialogTrigger>
            <DialogContent title="Add location" description="Staff are assigned to it under User accounts.">
              <LocationForm onSaved={() => setAdding(false)} />
            </DialogContent>
          </Dialog>
        }
      />
      {archiveError && (
        <Alert className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <span>{archiveError}</span>
          <Button size="sm" variant="outline" onClick={() => setArchiveError(null)}>
            Dismiss
          </Button>
        </Alert>
      )}
      <QueryState query={locations}>
        {(list) => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Location</TableHead>
                <TableHead>Time zone</TableHead>
                <TableHead>Labour rate</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((l) => (
                <TableRow key={l.id}>
                  <TableCell>
                    <div className="font-medium">{l.name}</div>
                    <div className="text-xs text-muted-foreground">
                      {l.code}
                      {l.address ? ` · ${l.address}` : ''}
                    </div>
                  </TableCell>
                  <TableCell>
                    {l.timeZone.replaceAll('_', ' ')}
                    {!l.ownTimeZone && <InheritedNote />}
                  </TableCell>
                  <TableCell>
                    {formatMoney(l.labourRate, l.currency)}/h
                    {l.ownLabourRate === null || l.ownLabourRate === undefined ? <InheritedNote /> : null}
                  </TableCell>
                  <TableCell>
                    <Badge variant={l.status === 'ACTIVE' ? 'success' : 'outline'}>
                      {l.status === 'ACTIVE' ? 'Active' : 'Archived'}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <EditLocation location={l} />
                      <LocationBillingButton location={l} />
                      {l.status === 'ACTIVE' ? (
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={archive.isPending}
                          aria-label={`Archive ${l.name}`}
                          onClick={() => archive.mutate({ id: l.id, name: l.name })}
                        >
                          Archive
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          disabled={reactivate.isPending}
                          aria-label={`Reactivate ${l.name}`}
                          onClick={() => reactivate.mutate(l.id)}
                        >
                          Reactivate
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </QueryState>
      {workshop.data && (
        <p className="mt-4 text-sm text-muted-foreground">
          Workshop defaults: {workshop.data.timeZone.replaceAll('_', ' ')},{' '}
          {formatMoney(workshop.data.labourRate, workshop.data.currency)}/h. Currency is the same everywhere.
        </p>
      )}
    </>
  );
}

function InheritedNote() {
  return <span className="ml-1 text-xs text-muted-foreground">(workshop default)</span>;
}

function EditLocation({ location }: { location: Location }) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" aria-label={`Edit ${location.name}`}>
          Edit
        </Button>
      </DialogTrigger>
      <DialogContent
        title={`Edit ${location.name}`}
        description="Changes apply to new bookings and job cards."
      >
        <LocationForm location={location} onSaved={() => setOpen(false)} />
      </DialogContent>
    </Dialog>
  );
}

function LocationForm({ location, onSaved }: { location?: Location; onSaved: () => void }) {
  const queryClient = useQueryClient();
  const workshop = useWorkshop();
  const zones = useMemo(() => timeZones(), []);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormInput, unknown, FormOutput>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: location?.name ?? '',
      code: location?.code ?? '',
      phone: location?.phone ?? '',
      email: location?.email ?? '',
      address: location?.address ?? '',
      timeZone: location?.ownTimeZone ?? '',
      labourRate:
        location?.ownLabourRate !== null && location?.ownLabourRate !== undefined
          ? String(location.ownLabourRate)
          : '',
    },
  });
  const save = useAction(
    (values: FormOutput) => {
      const body = {
        name: values.name,
        code: values.code,
        phone: values.phone || undefined,
        email: values.email || undefined,
        address: values.address || undefined,
        timeZone: values.timeZone || undefined,
        labourRate: values.labourRate === '' ? undefined : Number(values.labourRate),
      };
      return location
        ? locationsApi.update(location.id, { ...body, version: location.version })
        : locationsApi.create(body);
    },
    {
      success: (l) => `${l.name} saved`,
      onSuccess: () => {
        // Bookings, boards and job cards may show this location's name, time zone or rate.
        void queryClient.invalidateQueries();
        onSaved();
      },
      onError: (error) => {
        if (error instanceof ApiError) {
          if (error.code === 'code-taken' || error.code === 'invalid-code') {
            setError('code', { message: error.message });
            return true;
          }
          if (error.code === 'invalid-time-zone') {
            setError('timeZone', { message: error.message });
            return true;
          }
        }
        return false;
      },
    },
  );
  const defaultZone = workshop.data?.timeZone.replaceAll('_', ' ') ?? 'the workshop setting';
  const defaultRate = workshop.data
    ? formatMoney(workshop.data.labourRate, workshop.data.currency)
    : 'the workshop rate';

  return (
    <form
      noValidate
      aria-label={location ? 'Edit location' : 'Add location'}
      className="grid gap-4"
      onSubmit={handleSubmit(async (values) => {
        await save.mutateAsync(values).catch(() => undefined);
      })}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Name" error={errors.name?.message}>
          <Input {...register('name')} />
        </Field>
        <Field label="Code" error={errors.code?.message} hint="Short and unique, e.g. north">
          <Input autoCapitalize="none" {...register('code')} />
        </Field>
        <Field label="Phone" error={errors.phone?.message}>
          <Input type="tel" {...register('phone')} />
        </Field>
        <Field label="Email" error={errors.email?.message}>
          <Input type="email" {...register('email')} />
        </Field>
      </div>
      <Field label="Address" error={errors.address?.message}>
        <Textarea rows={2} {...register('address')} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Time zone" error={errors.timeZone?.message}>
          <Select {...register('timeZone')}>
            <option value="">Workshop default ({defaultZone})</option>
            {zones.map((zone) => (
              <option key={zone} value={zone}>
                {zone.replaceAll('_', ' ')}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Labour rate per hour" error={errors.labourRate?.message} hint={`Empty: ${defaultRate}`}>
          <Input inputMode="decimal" {...register('labourRate')} />
        </Field>
      </div>
      <Button type="submit" disabled={isSubmitting}>
        {location ? 'Save location' : 'Add location'}
      </Button>
    </form>
  );
}
