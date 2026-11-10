import type { JobCard } from '@/api/types';
import { formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

/** Where the job card is in its life: check-in → inspection → approval → work → done. */
export function JobCardTimeline({ card }: { card: JobCard }) {
  const inspected = card.findings[0]?.createdAt;
  const steps = [
    { label: 'Checked in', at: card.createdAt },
    { label: 'Inspected', at: inspected },
    { label: 'Sent for approval', at: card.submittedAt },
    { label: card.status === 'DECLINED' || (card.status === 'COMPLETED' && !card.startedAt) ? 'Declined' : 'Customer decided', at: card.decidedAt },
    { label: 'Work started', at: card.startedAt },
    { label: 'Completed', at: card.completedAt },
  ];
  return (
    <ol aria-label="Job card progress" className="grid gap-3 sm:grid-cols-6">
      {steps.map((step, index) => {
        const done = !!step.at;
        return (
          <li key={step.label} className="flex items-start gap-2 sm:flex-col">
            <span
              aria-hidden
              className={cn(
                'mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold',
                done ? 'border-primary bg-primary text-primary-foreground' : 'bg-background text-muted-foreground',
              )}
            >
              {index + 1}
            </span>
            <span className="text-sm">
              <span className={cn('block font-medium', !done && 'text-muted-foreground')}>{step.label}</span>
              <span className="block text-xs text-muted-foreground">
                {done ? formatDateTime(step.at) : 'Not yet'}
                <span className="sr-only">{done ? ' (done)' : ''}</span>
              </span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}
