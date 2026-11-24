import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { cannedJobsApi, remindersApi } from '@/api/endpoints';
import type { ReminderSettings, ServiceInterval, ServiceIntervalRequest } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { useAction } from '@/lib/use-action';

const INTERVALS_KEY = ['service-intervals'] as const;
const SETTINGS_KEY = ['service-reminders', 'settings'] as const;

/** Settings → Service intervals (admins): the shop's own maintenance schedule. */
export function ServiceIntervalsSection() {
  const intervals = useQuery({ queryKey: INTERVALS_KEY, queryFn: remindersApi.intervals });
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: remindersApi.settings });
  const [editing, setEditing] = useState<ServiceInterval | 'new' | null>(null);
  return (
    <section aria-labelledby="intervals-heading" className="grid max-w-3xl gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="intervals-heading" className="text-xl font-semibold tracking-tight">
            Service intervals
          </h2>
          <p className="text-sm text-muted-foreground">
            When a finished job includes one of these, the vehicle is due again after the miles or the months,
            whichever comes first. Due vehicles show under <em>Due soon</em> and at check-in.
          </p>
        </div>
        <Button variant="outline" onClick={() => setEditing('new')}>
          Add interval
        </Button>
      </div>
      <QueryState query={intervals}>
        {(list) => (
          <Card>
            <CardContent className="grid gap-2 pt-6">
              {list.map((interval) => (
                <div
                  key={interval.id}
                  className="flex flex-wrap items-center justify-between gap-2 border-b pb-2 last:border-0 last:pb-0"
                >
                  <div className="text-sm">
                    <span className="font-medium">{interval.name}</span>{' '}
                    <span className="text-muted-foreground">· {every(interval)}</span>{' '}
                    {!interval.active && <Badge variant="outline">Off</Badge>}
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setEditing(interval)}
                    aria-label={`Edit ${interval.name}`}
                  >
                    Edit
                  </Button>
                </div>
              ))}
            </CardContent>
          </Card>
        )}
      </QueryState>
      <QueryState query={settings}>{(s) => <SettingsCard key={s.version} settings={s} />}</QueryState>
      {editing && (
        <IntervalDialog interval={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      )}
    </section>
  );
}

function every(i: ServiceInterval) {
  const parts = [
    i.miles !== null ? `${i.miles.toLocaleString('en-US')} mi` : null,
    i.months !== null ? `${i.months} ${i.months === 1 ? 'month' : 'months'}` : null,
  ].filter(Boolean);
  return `every ${parts.join(' or ')}`;
}

const whole = (value: string, min: number, max: number) =>
  value.trim() === '' || (Number.isInteger(Number(value)) && Number(value) >= min && Number(value) <= max);

function IntervalDialog({ interval, onClose }: { interval: ServiceInterval | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const jobs = useQuery({
    queryKey: ['canned-jobs', 'all'],
    queryFn: () => cannedJobsApi.list({ includeInactive: true }),
  });
  const [name, setName] = useState(interval?.name ?? '');
  const [miles, setMiles] = useState(interval?.miles != null ? String(interval.miles) : '');
  const [months, setMonths] = useState(interval?.months != null ? String(interval.months) : '');
  const [keywords, setKeywords] = useState((interval?.keywords ?? []).join(', '));
  const [cannedJobIds, setCannedJobIds] = useState<string[]>(interval?.cannedJobIds ?? []);
  const [active, setActive] = useState(interval?.active ?? true);
  const [tried, setTried] = useState(false);
  const milesOk = whole(miles, 100, 200000);
  const monthsOk = whole(months, 1, 120);
  const valid = name.trim() !== '' && milesOk && monthsOk && (miles.trim() !== '' || months.trim() !== '');

  const done = () => {
    void queryClient.invalidateQueries({ queryKey: INTERVALS_KEY });
    onClose();
  };
  const save = useAction(
    () => {
      const body: ServiceIntervalRequest = {
        name: name.trim(),
        miles: miles.trim() === '' ? null : Number(miles),
        months: months.trim() === '' ? null : Number(months),
        keywords: keywords
          .split(',')
          .map((k) => k.trim())
          .filter(Boolean),
        cannedJobIds,
        active,
        version: interval?.version ?? null,
      };
      return interval ? remindersApi.updateInterval(interval.id, body) : remindersApi.createInterval(body);
    },
    { success: (saved) => `${saved.name} saved`, onSuccess: done },
  );
  const remove = useAction(() => remindersApi.deleteInterval(interval?.id ?? ''), {
    success: 'Interval deleted',
    onSuccess: done,
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={interval ? `Edit ${interval.name}` : 'New service interval'}
        description="Due again after the miles or the months, whichever comes first."
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
          <Field label="Service" error={tried && name.trim() === '' ? 'Name the service' : undefined}>
            <Input
              maxLength={80}
              placeholder="e.g. Oil change"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Every (miles)" error={!milesOk ? 'Whole miles, 100 to 200,000' : undefined}>
              <Input inputMode="numeric" value={miles} onChange={(e) => setMiles(e.target.value)} />
            </Field>
            <Field label="Or every (months)" error={!monthsOk ? 'Whole months, 1 to 120' : undefined}>
              <Input inputMode="numeric" value={months} onChange={(e) => setMonths(e.target.value)} />
            </Field>
          </div>
          {tried && miles.trim() === '' && months.trim() === '' && (
            <p role="alert" className="text-sm text-destructive">
              Give the miles, the months or both.
            </p>
          )}
          <Field
            label="Keywords"
            hint="Comma-separated. Typed-in work whose description contains one counts, e.g. oil change, oil and filter."
          >
            <Input value={keywords} onChange={(e) => setKeywords(e.target.value)} />
          </Field>
          {(jobs.data ?? []).length > 0 && (
            <fieldset className="grid gap-1">
              <legend className="mb-1 text-sm font-medium">Canned jobs that count</legend>
              {(jobs.data ?? []).map((job) => (
                <label key={job.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4"
                    checked={cannedJobIds.includes(job.id)}
                    onChange={(e) =>
                      setCannedJobIds(
                        e.target.checked
                          ? [...cannedJobIds, job.id]
                          : cannedJobIds.filter((id) => id !== job.id),
                      )
                    }
                  />
                  {job.name}
                </label>
              ))}
            </fieldset>
          )}
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
            />
            On (switched off, nothing new is recorded and it isn't shown as due)
          </label>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={save.isPending}>
              {save.isPending ? 'Saving…' : 'Save'}
            </Button>
            {interval && (
              <Button
                type="button"
                variant="outline"
                disabled={remove.isPending}
                onClick={() => remove.mutate(undefined)}
              >
                Delete
              </Button>
            )}
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function SettingsCard({ settings }: { settings: ReminderSettings }) {
  const queryClient = useQueryClient();
  const [milesPerDay, setMilesPerDay] = useState(String(settings.milesPerDay));
  const [days, setDays] = useState(String(settings.remindDaysBefore));
  const valid =
    whole(milesPerDay, 1, 500) && milesPerDay.trim() !== '' && whole(days, 0, 90) && days.trim() !== '';
  const save = useAction(
    () =>
      remindersApi.updateSettings({
        enabled: settings.enabled,
        remindDaysBefore: Number(days),
        milesPerDay: Number(milesPerDay),
        version: settings.version,
      }),
    {
      success: 'Reminder settings saved',
      onSuccess: (saved) => queryClient.setQueryData(SETTINGS_KEY, saved),
    },
  );
  return (
    <Card>
      <CardHeader>
        <CardTitle>How due dates are worked out</CardTitle>
        <CardDescription>
          A vehicle's miles a day come from its visits here; for one seen only once, Ramplight assumes the
          number below. Customers will be reminded the given number of days before a service is due.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          noValidate
          className="grid gap-4 sm:grid-cols-[12rem_12rem_auto] sm:items-end sm:justify-start"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) {
              save.mutate(undefined);
            }
          }}
        >
          <Field label="Miles a day (assumed)">
            <Input inputMode="numeric" value={milesPerDay} onChange={(e) => setMilesPerDay(e.target.value)} />
          </Field>
          <Field label="Remind days before">
            <Input inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value)} />
          </Field>
          <Button type="submit" variant="outline" disabled={!valid || save.isPending}>
            Save
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
