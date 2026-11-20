import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { announcementsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { Announcement } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { PUBLIC_WORKSHOP_QUERY_KEY } from '@/features/workshop/use-public-workshop';
import {
  formatDateTime,
  isoToLocalInput,
  localInputToIso,
  localInputToMillis,
  zoneLabel,
} from '@/lib/format';
import { useAction } from '@/lib/use-action';
import { cn } from '@/lib/utils';

export const ANNOUNCEMENTS_QUERY_KEY = ['branding', 'announcements'] as const;
/** Same limits as auth-service. */
export const MAX_ANNOUNCEMENTS = 10;
const TEXT_MAX = 200;

const STATES: Record<Announcement['state'], { label: string; variant: 'success' | 'secondary' | 'outline' }> =
  {
    LIVE: { label: 'Live', variant: 'success' },
    SCHEDULED: { label: 'Scheduled', variant: 'secondary' },
    ENDED: { label: 'Ended', variant: 'outline' },
  };

const schema = z
  .object({
    text: z.string().trim().min(1, 'Write the announcement').max(TEXT_MAX, `At most ${TEXT_MAX} characters`),
    style: z.enum(['INFO', 'IMPORTANT']),
    startsAt: z.string(),
    endsAt: z.string(),
  })
  .superRefine((values, ctx) => {
    const starts = localInputToMillis(values.startsAt);
    const ends = localInputToMillis(values.endsAt);
    if (values.startsAt && Number.isNaN(starts)) {
      ctx.addIssue({ code: 'custom', path: ['startsAt'], message: 'Enter a date and time' });
    }
    if (values.endsAt && Number.isNaN(ends)) {
      ctx.addIssue({ code: 'custom', path: ['endsAt'], message: 'Enter a date and time' });
    }
    if (!Number.isNaN(starts) && !Number.isNaN(ends) && ends <= starts) {
      ctx.addIssue({ code: 'custom', path: ['endsAt'], message: 'Must be after the start' });
    }
  });

type AnnouncementForm = z.infer<typeof schema>;

function useRefresh() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ANNOUNCEMENTS_QUERY_KEY });
    // The line at the top of every page follows at once.
    void queryClient.invalidateQueries({ queryKey: PUBLIC_WORKSHOP_QUERY_KEY });
  };
}

/**
 * The workshop's announcements: up to 10, each with a style and optional dates. The live ones
 * show at the top of every page as one line, important first, with "N more" for the rest.
 */
export function AnnouncementsCard() {
  const list = useQuery({ queryKey: ANNOUNCEMENTS_QUERY_KEY, queryFn: announcementsApi.list });
  const [editing, setEditing] = useState<Announcement | 'new' | null>(null);
  const refresh = useRefresh();
  const remove = useAction(announcementsApi.remove, { success: 'Announcement deleted', onSuccess: refresh });
  const full = (list.data?.length ?? 0) >= MAX_ANNOUNCEMENTS;

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1.5">
          <CardTitle>Announcements</CardTitle>
          <CardDescription>
            Holiday hours, seasonal offers. The live ones show at the top of every page as one line, the
            important one first, with "more" to open the rest. Up to {MAX_ANNOUNCEMENTS}.
          </CardDescription>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={full || list.isPending}
          title={full ? `At most ${MAX_ANNOUNCEMENTS}: delete one first` : undefined}
          onClick={() => setEditing('new')}
        >
          Add announcement
        </Button>
      </CardHeader>
      <CardContent>
        <QueryState query={list}>
          {(announcements) =>
            announcements.length === 0 ? (
              <p className="text-sm text-muted-foreground">No announcements yet.</p>
            ) : (
              <ul className="grid gap-2" aria-label="Announcements">
                {announcements.map((a) => (
                  <li
                    key={a.id}
                    data-testid="announcement-row"
                    className="flex flex-wrap items-start gap-3 rounded-md border px-3 py-2.5"
                  >
                    <div className="grid min-w-0 flex-1 gap-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant={STATES[a.state].variant}>{STATES[a.state].label}</Badge>
                        {a.style === 'IMPORTANT' && <Badge variant="warning">Important</Badge>}
                      </div>
                      <p
                        className={cn(
                          'm-0 break-words text-sm',
                          a.state === 'ENDED' && 'text-muted-foreground',
                        )}
                      >
                        {a.text}
                      </p>
                      <p className="m-0 text-xs text-muted-foreground">{when(a)}</p>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      <Button type="button" variant="ghost" size="sm" onClick={() => setEditing(a)}>
                        Edit<span className="sr-only">: {a.text}</span>
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={remove.isPending}
                        onClick={() => remove.mutate(a.id)}
                      >
                        Delete<span className="sr-only">: {a.text}</span>
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            )
          }
        </QueryState>
      </CardContent>
      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        {editing !== null && (
          <DialogContent
            title={editing === 'new' ? 'Add announcement' : 'Edit announcement'}
            description={`One line of plain text. Leave the dates empty to show it until you delete it. Times are in ${zoneLabel() || 'the workshop time zone'}.`}
            className="max-w-lg"
          >
            <AnnouncementEditor
              key={editing === 'new' ? 'new' : `${editing.id}-${editing.version}`}
              announcement={editing === 'new' ? null : editing}
              onDone={() => setEditing(null)}
            />
          </DialogContent>
        )}
      </Dialog>
    </Card>
  );
}

function when(a: Announcement): string {
  if (!a.startsAt && !a.endsAt) {
    return 'Shown until deleted';
  }
  const from = a.startsAt ? `From ${formatDateTime(a.startsAt)}` : 'Now';
  const until = a.endsAt ? ` until ${formatDateTime(a.endsAt)}` : ', no end date';
  return from + until;
}

function AnnouncementEditor({
  announcement,
  onDone,
}: {
  announcement: Announcement | null;
  onDone: () => void;
}) {
  const refresh = useRefresh();
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<AnnouncementForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      text: announcement?.text ?? '',
      style: announcement?.style ?? 'INFO',
      startsAt: announcement?.startsAt ? isoToLocalInput(announcement.startsAt) : '',
      endsAt: announcement?.endsAt ? isoToLocalInput(announcement.endsAt) : '',
    },
  });
  const onError = (error: unknown) => {
    if (error instanceof ApiError) {
      if (error.code === 'invalid-announcement') {
        setError('text', { message: error.message });
        return true;
      }
      if (error.code === 'invalid-announcement-window') {
        setError('endsAt', { message: error.message });
        return true;
      }
      if (error.code === 'concurrent-update') {
        void queryClient.invalidateQueries({ queryKey: ANNOUNCEMENTS_QUERY_KEY });
      }
    }
    return false;
  };
  const done = () => {
    refresh();
    onDone();
  };
  const create = useAction(announcementsApi.create, {
    success: 'Announcement added',
    onSuccess: done,
    onError,
  });
  const update = useAction(announcementsApi.update, {
    success: 'Announcement saved',
    onSuccess: done,
    onError,
  });

  const text = watch('text') ?? '';
  const style = watch('style');

  return (
    <form
      noValidate
      aria-label="Announcement"
      className="grid gap-4"
      onSubmit={handleSubmit(async (values) => {
        const body = {
          text: values.text,
          style: values.style,
          startsAt: values.startsAt ? localInputToIso(values.startsAt) : null,
          endsAt: values.endsAt ? localInputToIso(values.endsAt) : null,
        };
        const saving = announcement
          ? update.mutateAsync({ ...body, id: announcement.id, version: announcement.version })
          : create.mutateAsync(body);
        await saving.catch(() => undefined);
      })}
    >
      <Field label="Message" error={errors.text?.message} hint={`${text.length}/${TEXT_MAX} characters`}>
        <Textarea rows={2} maxLength={TEXT_MAX} {...register('text')} />
      </Field>
      <Field label="Style" error={errors.style?.message}>
        <Select {...register('style')}>
          <option value="INFO">Information</option>
          <option value="IMPORTANT">Important (shown first)</option>
        </Select>
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Show from" error={errors.startsAt?.message}>
          <Input type="datetime-local" {...register('startsAt')} />
        </Field>
        <Field label="Show until" error={errors.endsAt?.message}>
          <Input type="datetime-local" {...register('endsAt')} />
        </Field>
      </div>
      {text.trim() && (
        <div>
          <div className="mb-1 text-xs text-muted-foreground">Preview</div>
          <div
            className={cn(
              'rounded-md border px-4 py-2.5 text-sm',
              style === 'IMPORTANT'
                ? 'border-warning/50 bg-warning/20 font-semibold'
                : 'border-primary/20 bg-primary/10',
            )}
          >
            {text}
          </div>
        </div>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {announcement ? 'Save announcement' : 'Add announcement'}
        </Button>
      </div>
    </form>
  );
}
