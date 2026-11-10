import { useQueries } from '@tanstack/react-query';
import { Link } from 'react-router';

import { jobCardsApi } from '@/api/endpoints';
import type { JobCardStatus, JobCardSummary } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { JobCardStatusBadge } from '@/components/common/StatusBadge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { formatDateTime, formatMileage } from '@/lib/format';

const SECTIONS: { title: string; description: string; statuses: JobCardStatus[] }[] = [
  { title: 'In progress', description: 'Log work, parts and labour, then complete.', statuses: ['IN_PROGRESS'] },
  {
    title: 'Ready to start',
    description: 'The customer approved the work.',
    statuses: ['APPROVED', 'PARTIALLY_APPROVED'],
  },
  { title: 'Needs inspection', description: 'Checked in and waiting for findings.', statuses: ['OPEN', 'INSPECTED'] },
];

/** A mechanic's work queue, grouped by what needs doing next. */
export function MyJobsPage() {
  return (
    <>
      <PageHeader title="My jobs" description="Vehicles in the workshop, grouped by what they need next." />
      <div className="grid gap-8">
        {SECTIONS.map((section) => (
          <JobSection key={section.title} {...section} />
        ))}
      </div>
    </>
  );
}

function JobSection({ title, description, statuses }: { title: string; description: string; statuses: JobCardStatus[] }) {
  // aria-labelledby takes a space-separated list of ids, so the id must not contain spaces.
  const headingId = `section-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
  const results = useQueries({
    queries: statuses.map((status) => ({
      queryKey: ['job-cards', { status, size: 50 }],
      queryFn: () => jobCardsApi.list({ status, size: 50 }),
    })),
  });
  const loading = results.some((r) => r.isPending);
  const cards: JobCardSummary[] = results.flatMap((r) => r.data?.content ?? []);

  return (
    <section aria-labelledby={headingId} className="grid gap-3">
      <div>
        <h2 id={headingId} className="text-lg font-semibold">
          {title} <span className="text-muted-foreground">({loading ? '…' : cards.length})</span>
        </h2>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      {loading ? (
        <Skeleton className="h-24" />
      ) : cards.length === 0 ? (
        <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">Nothing here right now.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {cards.map((card) => (
            <li key={card.id}>
              <Link to={`/job-cards/${card.id}`} className="group block h-full">
                <Card className="h-full transition-colors group-hover:border-primary/50">
                  <CardHeader>
                    <CardTitle className="flex items-center justify-between gap-2">
                      {card.registration}
                      <JobCardStatusBadge status={card.status} />
                    </CardTitle>
                    <CardDescription>
                      {card.customerName} · {formatMileage(card.mileage)}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="text-xs text-muted-foreground">
                    Updated {formatDateTime(card.updatedAt)}
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
