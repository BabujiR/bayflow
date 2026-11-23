import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { cannedJobsApi } from '@/api/endpoints';
import type { CannedJob, CannedJobRequest } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { useLocationLookup, useLocations } from '@/features/locations/use-locations';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { formatMoney } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { CANNED_JOBS_QUERY_KEY } from './canned-jobs';

/** Settings → Canned jobs (managers and admins): the shop's ready-made packages of work. */
export function CannedJobsPage() {
  const all = useQuery({
    queryKey: [...CANNED_JOBS_QUERY_KEY, 'all'],
    queryFn: () => cannedJobsApi.list({ includeInactive: true }),
  });
  const [editing, setEditing] = useState<CannedJob | 'new' | null>(null);
  return (
    <div className="grid gap-6">
      <PageHeader
        title="Canned jobs"
        description="Work you do often, with its labour hours and usual parts. Advisors pick them when recommending work; mechanics add the usual parts in one go."
        actions={<Button onClick={() => setEditing('new')}>New canned job</Button>}
      />
      <QueryState query={all}>
        {(jobs) => (jobs.length === 0 ? <Empty /> : <JobTable jobs={jobs} onEdit={setEditing} />)}
      </QueryState>
      {editing && <JobDialog job={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function Empty() {
  return (
    <Card>
      <CardContent className="pt-6 text-sm text-muted-foreground">
        No canned jobs yet. Start with the ones you do every day, e.g. &ldquo;Synthetic oil change&rdquo; or
        &ldquo;Front brakes – pads &amp; rotors&rdquo;.
      </CardContent>
    </Card>
  );
}

function JobTable({ jobs, onEdit }: { jobs: CannedJob[]; onEdit: (job: CannedJob) => void }) {
  const locations = useLocationLookup();
  const workshop = useWorkshop();
  const currency = workshop.data?.currency ?? 'USD';
  const queryClient = useQueryClient();
  const setActive = useAction(
    ({ id, active }: { id: string; active: boolean }) => cannedJobsApi.setActive(id, active),
    {
      success: (job) => (job.active ? `${job.name} restored` : `${job.name} archived`),
      onSuccess: () => void queryClient.invalidateQueries({ queryKey: CANNED_JOBS_QUERY_KEY }),
    },
  );
  return (
    <Card>
      <CardContent className="overflow-x-auto pt-6">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Category</TableHead>
              {locations.hasSeveral && <TableHead>Location</TableHead>}
              <TableHead className="text-right">Labour</TableHead>
              <TableHead className="text-right">Parts</TableHead>
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {jobs.map((job) => (
              <TableRow key={job.id} className={job.active ? undefined : 'text-muted-foreground'}>
                <TableCell className="font-medium">
                  {job.name} {!job.active && <Badge variant="outline">Archived</Badge>}
                </TableCell>
                <TableCell>{job.category ?? '—'}</TableCell>
                {locations.hasSeveral && (
                  <TableCell>{job.locationId ? locations.nameOf(job.locationId) : 'All locations'}</TableCell>
                )}
                <TableCell className="text-right">{job.labourHours} h</TableCell>
                <TableCell className="text-right">
                  {job.parts.length > 0 ? formatMoney(job.partsTotal, currency) : '—'}
                </TableCell>
                <TableCell className="whitespace-nowrap text-right">
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => onEdit(job)}
                    aria-label={`Edit ${job.name}`}
                  >
                    Edit
                  </Button>{' '}
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={setActive.isPending}
                    onClick={() => setActive.mutate({ id: job.id, active: !job.active })}
                    aria-label={`${job.active ? 'Archive' : 'Restore'} ${job.name}`}
                  >
                    {job.active ? 'Archive' : 'Restore'}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

interface PartRow {
  key: number;
  partNumber: string;
  name: string;
  quantity: string;
  unitPrice: string;
}

let nextKey = 1;

const decimal = (value: string) => value.trim() !== '' && !Number.isNaN(Number(value)) && Number(value) >= 0;

function JobDialog({ job, onClose }: { job: CannedJob | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const locations = useLocations();
  const active = (locations.data ?? []).filter((l) => l.status === 'ACTIVE');
  const [name, setName] = useState(job?.name ?? '');
  const [category, setCategory] = useState(job?.category ?? '');
  const [description, setDescription] = useState(job?.description ?? '');
  const [hours, setHours] = useState(job ? String(job.labourHours) : '');
  const [locationId, setLocationId] = useState(job?.locationId ?? '');
  const [parts, setParts] = useState<PartRow[]>(
    (job?.parts ?? []).map((p) => ({
      key: nextKey++,
      partNumber: p.partNumber,
      name: p.name,
      quantity: String(p.quantity),
      unitPrice: String(p.unitPrice),
    })),
  );
  const [tried, setTried] = useState(false);

  const partsValid = parts.every(
    (p) =>
      p.partNumber.trim() !== '' &&
      p.name.trim() !== '' &&
      Number.isInteger(Number(p.quantity)) &&
      Number(p.quantity) >= 1 &&
      decimal(p.unitPrice),
  );
  const valid =
    name.trim() !== '' && description.trim() !== '' && decimal(hours) && Number(hours) <= 999 && partsValid;

  const save = useAction(
    () => {
      const body: CannedJobRequest = {
        locationId: locationId || null,
        name: name.trim(),
        category: category.trim() || null,
        description: description.trim(),
        labourHours: Number(hours),
        parts: parts.map((p) => ({
          partNumber: p.partNumber.trim(),
          name: p.name.trim(),
          quantity: Number(p.quantity),
          unitPrice: Number(p.unitPrice),
        })),
        version: job?.version ?? null,
      };
      return job ? cannedJobsApi.update(job.id, body) : cannedJobsApi.create(body);
    },
    {
      success: (saved) => `${saved.name} saved`,
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: CANNED_JOBS_QUERY_KEY });
        onClose();
      },
    },
  );
  const change = (key: number, patch: Partial<PartRow>) =>
    setParts(parts.map((p) => (p.key === key ? { ...p, ...patch } : p)));

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={job ? `Edit ${job.name}` : 'New canned job'}
        description="Labour is charged at each job card's labour rate; parts at the prices here."
        className="max-w-3xl"
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            setTried(true);
            if (valid) {
              save.mutate(undefined);
            }
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" error={tried && name.trim() === '' ? 'Enter a name' : undefined}>
              <Input
                maxLength={120}
                placeholder="e.g. Synthetic oil change"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </Field>
            <Field label="Category" hint="Optional, e.g. Maintenance or Brakes">
              <Input maxLength={60} value={category} onChange={(e) => setCategory(e.target.value)} />
            </Field>
          </div>
          <Field
            label="Description"
            hint="What the customer reads on the estimate."
            error={tried && description.trim() === '' ? 'Describe the work' : undefined}
          >
            <Textarea
              rows={2}
              maxLength={1000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Labour (hours)" error={tried && !decimal(hours) ? 'Enter the hours' : undefined}>
              <Input inputMode="decimal" value={hours} onChange={(e) => setHours(e.target.value)} />
            </Field>
            {active.length > 1 && (
              <Field label="Location">
                <Select value={locationId} onChange={(e) => setLocationId(e.target.value)}>
                  <option value="">All locations</option>
                  {active.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name}
                    </option>
                  ))}
                </Select>
              </Field>
            )}
          </div>

          <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-medium">Usual parts</legend>
            {parts.length === 0 && <p className="text-sm text-muted-foreground">No parts: labour only.</p>}
            {parts.map((p, index) => (
              <div key={p.key} className="grid gap-2 sm:grid-cols-[8rem_1fr_5rem_7rem_auto] sm:items-end">
                <Field label={`Part no. ${index + 1}`}>
                  <Input
                    maxLength={50}
                    value={p.partNumber}
                    onChange={(e) => change(p.key, { partNumber: e.target.value })}
                  />
                </Field>
                <Field label={`Part ${index + 1}`}>
                  <Input
                    maxLength={200}
                    value={p.name}
                    onChange={(e) => change(p.key, { name: e.target.value })}
                  />
                </Field>
                <Field label={`Qty ${index + 1}`}>
                  <Input
                    inputMode="numeric"
                    value={p.quantity}
                    onChange={(e) => change(p.key, { quantity: e.target.value })}
                  />
                </Field>
                <Field label={`Unit price ${index + 1}`}>
                  <Input
                    inputMode="decimal"
                    value={p.unitPrice}
                    onChange={(e) => change(p.key, { unitPrice: e.target.value })}
                  />
                </Field>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setParts(parts.filter((x) => x.key !== p.key))}
                  aria-label={`Remove part ${index + 1}`}
                >
                  Remove
                </Button>
              </div>
            ))}
            {tried && !partsValid && (
              <p role="alert" className="text-sm text-destructive">
                Every part needs a part number, a name, a whole quantity and a price.
              </p>
            )}
            <Button
              type="button"
              variant="outline"
              className="justify-self-start"
              disabled={parts.length >= 50}
              onClick={() =>
                setParts([
                  ...parts,
                  { key: nextKey++, partNumber: '', name: '', quantity: '1', unitPrice: '' },
                ])
              }
            >
              Add part
            </Button>
          </fieldset>

          <Button type="submit" className="justify-self-start" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
