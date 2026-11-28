import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { locationsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { Location, ReviewDelay } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useAction } from '@/lib/use-action';

import { LOCATIONS_QUERY_KEY } from './use-locations';

export const REVIEW_DELAYS: { value: ReviewDelay; label: string }[] = [
  { value: 'TWO_HOURS', label: '2 hours after payment' },
  { value: 'NEXT_MORNING', label: 'The next morning (9 AM)' },
  { value: 'TWO_DAYS', label: '2 days after payment' },
];

/** ADMIN: a location's Google review requests (its review link, and whether and when to ask). */
export function LocationReviewsButton({ location }: { location: Location }) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" aria-label={`Google reviews for ${location.name}`}>
          Google reviews
        </Button>
      </DialogTrigger>
      <DialogContent
        title={`Google reviews · ${location.name}`}
        description="Customers get a short e-mail with your review link after their invoice is paid in full."
        className="max-w-lg"
      >
        <ReviewsForm location={location} onSaved={() => setOpen(false)} />
      </DialogContent>
    </Dialog>
  );
}

function ReviewsForm({ location, onSaved }: { location: Location; onSaved: () => void }) {
  const queryClient = useQueryClient();
  const current = location.reviews;
  const [enabled, setEnabled] = useState(current?.enabled ?? false);
  const [url, setUrl] = useState(current?.url ?? '');
  const [delay, setDelay] = useState<ReviewDelay>(current?.delay ?? 'NEXT_MORNING');
  const [problem, setProblem] = useState<string | null>(null);
  const trimmed = url.trim();
  const urlProblem =
    trimmed !== '' && !/^https:\/\/\S+$/i.test(trimmed)
      ? 'Paste the full link, starting with https://'
      : enabled && trimmed === ''
        ? 'Add the link to turn review requests on'
        : null;

  const save = useAction(
    () => locationsApi.updateReviews(location.id, { enabled, url: trimmed || null, delay }, location.version),
    {
      success: (l) => `Google reviews saved for ${l.name}`,
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: LOCATIONS_QUERY_KEY });
        onSaved();
      },
      onError: (error) => {
        if (error instanceof ApiError && error.isClientError) {
          setProblem(error.message);
          return true;
        }
        return false;
      },
    },
  );

  return (
    <form
      noValidate
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        setProblem(null);
        if (!urlProblem) {
          void save.mutateAsync(undefined).catch(() => undefined);
        }
      }}
    >
      <Field
        label="Google review link"
        error={urlProblem ?? undefined}
        hint="In your Google Business Profile, choose “Ask for reviews” and copy the link."
      >
        <Input
          type="url"
          inputMode="url"
          placeholder="https://g.page/r/…/review"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
      </Field>
      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="mt-1"
          checked={enabled}
          onChange={(e) => setEnabled(e.target.checked)}
        />
        <span>
          <span className="font-medium">Ask customers for a Google review after they pay</span>
          <span className="block text-muted-foreground">
            Everyone with an e-mail address is asked, at most once every 90 days. Each e-mail has a link to
            stop them.
          </span>
        </span>
      </label>
      <Field label="When to send it">
        <Select value={delay} onChange={(e) => setDelay(e.target.value as ReviewDelay)}>
          {REVIEW_DELAYS.map((d) => (
            <option key={d.value} value={d.value}>
              {d.label}
            </option>
          ))}
        </Select>
      </Field>
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
      <Button type="submit" disabled={save.isPending}>
        Save Google reviews
      </Button>
    </form>
  );
}
