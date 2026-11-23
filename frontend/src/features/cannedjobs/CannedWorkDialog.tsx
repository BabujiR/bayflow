import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { cannedJobsApi, jobCardsApi } from '@/api/endpoints';
import type { CannedJob, JobCard, Recommendation } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { formatMoney } from '@/lib/format';
import { useAction } from '@/lib/use-action';

/**
 * A mechanic adds a canned job's usual parts and labour to an approved item in one go: each
 * ticked, with the quantity and hours as used.
 */
export function CannedWorkDialog({
  card,
  item,
  onClose,
  onSaved,
}: {
  card: JobCard;
  item: Recommendation & { cannedJobId?: string };
  onClose: () => void;
  onSaved: (card: JobCard) => void;
}) {
  const job = useQuery({
    queryKey: ['canned-jobs', 'one', item.cannedJobId],
    queryFn: () => cannedJobsApi.get(item.cannedJobId ?? ''),
    enabled: Boolean(item.cannedJobId),
  });
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title="Add usual parts & labour"
        description={`For “${item.description}”. Untick what you didn't use and correct the quantities.`}
        className="max-w-2xl"
      >
        <QueryState query={job}>
          {(j) => <WorkForm card={card} item={item} job={j} onClose={onClose} onSaved={onSaved} />}
        </QueryState>
      </DialogContent>
    </Dialog>
  );
}

function WorkForm({
  card,
  item,
  job,
  onClose,
  onSaved,
}: {
  card: JobCard;
  item: Recommendation;
  job: CannedJob;
  onClose: () => void;
  onSaved: (card: JobCard) => void;
}) {
  const [parts, setParts] = useState(job.parts.map((p) => ({ ...p, use: true, qty: String(p.quantity) })));
  const [useLabour, setUseLabour] = useState(job.labourHours > 0);
  const [hours, setHours] = useState(String(job.labourHours));
  const chosen = parts.filter((p) => p.use);
  const partsValid = chosen.every(
    (p) => Number.isInteger(Number(p.qty)) && Number(p.qty) >= 1 && Number(p.qty) <= 1000,
  );
  const hoursValid = !useLabour || (Number(hours) > 0 && Number(hours) <= 24);
  const valid = (chosen.length > 0 || useLabour) && partsValid && hoursValid;

  const save = useAction(
    () =>
      jobCardsApi.addWork(card.id, item.id, {
        parts: chosen.map((p) => ({
          partNumber: p.partNumber,
          name: p.name,
          quantity: Number(p.qty),
          unitPrice: p.unitPrice,
        })),
        hours: useLabour ? Number(hours) : null,
      }),
    {
      success: 'Parts and labour added',
      invalidate: [['job-cards']],
      onSuccess: (saved) => {
        onSaved(saved);
        onClose();
      },
    },
  );

  return (
    <form
      noValidate
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (valid) {
          save.mutate(undefined);
        }
      }}
    >
      {parts.length > 0 && (
        <fieldset className="grid gap-2">
          <legend className="mb-1 text-sm font-medium">Parts</legend>
          {parts.map((p, index) => (
            <div key={p.id} className="grid grid-cols-[auto_1fr_5rem] items-center gap-3 text-sm">
              <input
                type="checkbox"
                className="size-4"
                checked={p.use}
                aria-label={`Use ${p.name}`}
                onChange={(e) =>
                  setParts(parts.map((x, i) => (i === index ? { ...x, use: e.target.checked } : x)))
                }
              />
              <span>
                {p.name}{' '}
                <span className="text-muted-foreground">
                  ({p.partNumber}) · {formatMoney(p.unitPrice, card.currency)} each
                </span>
              </span>
              <Input
                inputMode="numeric"
                aria-label={`Quantity of ${p.name}`}
                disabled={!p.use}
                value={p.qty}
                onChange={(e) =>
                  setParts(parts.map((x, i) => (i === index ? { ...x, qty: e.target.value } : x)))
                }
              />
            </div>
          ))}
        </fieldset>
      )}
      <fieldset className="grid grid-cols-[auto_1fr_5rem] items-center gap-3 text-sm">
        <legend className="mb-1 text-sm font-medium">Labour</legend>
        <input
          type="checkbox"
          className="size-4"
          checked={useLabour}
          aria-label="Add the labour"
          onChange={(e) => setUseLabour(e.target.checked)}
        />
        <span>Hours at {formatMoney(card.labourRate, card.currency)}/h</span>
        <Input
          inputMode="decimal"
          aria-label="Labour hours"
          disabled={!useLabour}
          value={hours}
          onChange={(e) => setHours(e.target.value)}
        />
      </fieldset>
      {!valid && (
        <p role="alert" className="text-sm text-destructive">
          {chosen.length === 0 && !useLabour
            ? 'Tick at least one part or the labour.'
            : 'Quantities are whole numbers from 1; hours from 0.01 to 24.'}
        </p>
      )}
      <Button type="submit" className="justify-self-start" disabled={!valid || save.isPending}>
        {save.isPending ? 'Adding…' : 'Add to the job'}
      </Button>
    </form>
  );
}
