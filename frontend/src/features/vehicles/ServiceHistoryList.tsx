import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { historyApi } from '@/api/endpoints';
import { EmptyState } from '@/components/common/EmptyState';
import { QueryState } from '@/components/common/QueryState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate, formatMileage, formatMoney } from '@/lib/format';

/** A vehicle's completed jobs, newest first, each with the work, parts and labour. */
export function ServiceHistoryList({ vehicleId }: { vehicleId: string }) {
  const history = useQuery({
    queryKey: ['history', vehicleId],
    queryFn: () => historyApi.forVehicle(vehicleId, { size: 50 }),
  });

  return (
    <QueryState query={history}>
      {(page) =>
        page.content.length === 0 ? (
          <EmptyState
            title="No service history yet"
            description="Completed jobs appear here with the work done, parts and cost."
          />
        ) : (
          <ol className="grid gap-4">
            {page.content.map((entry) => (
              <li key={entry.id}>
                <Card>
                  <CardHeader className="flex-row flex-wrap items-baseline justify-between gap-2">
                    <CardTitle>{formatDate(entry.serviceDate)}</CardTitle>
                    <span className="text-sm text-muted-foreground">
                      {formatMileage(entry.mileage)} · {formatMoney(entry.totalCost, entry.currency)}
                    </span>
                  </CardHeader>
                  <CardContent className="grid gap-3 text-sm">
                    {(entry.details?.work ?? []).length > 0 ? (
                      <div>
                        <h3 className="font-medium">Work performed</h3>
                        <ul className="mt-1 list-disc pl-5">
                          {(entry.details?.work ?? []).map((work) => (
                            <li key={work.recommendation}>
                              <span className="font-medium">{work.recommendation}</span>
                              {work.workPerformed && work.workPerformed.length > 0 && (
                                <span className="text-muted-foreground"> — {work.workPerformed.join('; ')}</span>
                              )}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ) : (
                      <p className="text-muted-foreground">Inspection only — no work was carried out.</p>
                    )}
                    {(entry.details?.parts ?? []).length > 0 && (
                      <div>
                        <h3 className="font-medium">Parts</h3>
                        <ul className="mt-1 list-disc pl-5">
                          {(entry.details?.parts ?? []).map((part, index) => (
                            <li key={`${part.partNumber}-${index}`}>
                              {part.quantity} × {part.name} ({part.partNumber}) —{' '}
                              {formatMoney(part.lineTotal, entry.currency)}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {(entry.details?.declined ?? []).length > 0 && (
                      <p className="text-muted-foreground">Declined: {(entry.details?.declined ?? []).join(', ')}</p>
                    )}
                    <div className="flex flex-wrap justify-between gap-2 border-t pt-3">
                      <span>
                        Parts {formatMoney(entry.partsTotal, entry.currency)} · Labour{' '}
                        {formatMoney(entry.labourTotal, entry.currency)}
                      </span>
                      <Link to={`/job-cards/${entry.jobCardId}`} className="font-medium text-primary hover:underline">
                        View job card
                      </Link>
                    </div>
                  </CardContent>
                </Card>
              </li>
            ))}
          </ol>
        )
      }
    </QueryState>
  );
}
