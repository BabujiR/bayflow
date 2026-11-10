import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';

import { jobCardsApi } from '@/api/endpoints';
import type { JobCard, Recommendation } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { ApprovalBadge, JobCardStatusBadge, SeverityBadge } from '@/components/common/StatusBadge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDateTime, formatMileage, formatMoney } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { FindingForm, RecommendationForm, WorkForms } from './JobCardForms';
import { JobCardTimeline } from './JobCardTimeline';

/**
 * One job card, shared by every role. Each role sees only the actions it may take:
 * mechanics and advisors record findings, advisors recommend work and submit it, customers
 * (or advisors on their behalf) decide, mechanics do the work, and either completes it.
 */
export function JobCardDetailPage() {
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['job-card', id], queryFn: () => jobCardsApi.get(id) });
  return <QueryState query={query}>{(card) => <JobCardView card={card} />}</QueryState>;
}

function useJobCardAction<V>(id: string, action: (variables: V) => Promise<JobCard>, success: string) {
  const queryClient = useQueryClient();
  return useAction(action, {
    success,
    invalidate: [['job-cards']],
    onSuccess: (card) => queryClient.setQueryData(['job-card', id], card),
  });
}

function JobCardView({ card }: { card: JobCard }) {
  const { hasRole } = useAuth();
  const advisor = hasRole('SERVICE_ADVISOR');
  const mechanic = hasRole('MECHANIC');
  const customer = hasRole('CUSTOMER');
  const { status } = card;
  const inspecting = status === 'OPEN' || status === 'INSPECTED';
  const approved = card.recommendations.filter((r) => r.approvalStatus === 'APPROVED');
  const workPhase = ['APPROVED', 'PARTIALLY_APPROVED', 'IN_PROGRESS', 'COMPLETED'].includes(status);
  const money = (amount?: number | null) => formatMoney(amount, card.currency);

  const addFinding = useJobCardAction(card.id, (v: Parameters<typeof jobCardsApi.addFinding>[1]) => jobCardsApi.addFinding(card.id, v), 'Finding recorded');
  const addRecommendation = useJobCardAction(
    card.id,
    (v: Parameters<typeof jobCardsApi.addRecommendation>[1]) => jobCardsApi.addRecommendation(card.id, v),
    'Recommendation added',
  );
  const submit = useJobCardAction(card.id, () => jobCardsApi.submit(card.id), 'Sent to the customer for approval');
  const decide = useJobCardAction(
    card.id,
    ({ itemId, decision }: { itemId: string; decision: 'approve' | 'decline' }) => jobCardsApi.decide(card.id, itemId, decision),
    'Decision recorded',
  );
  const start = useJobCardAction(card.id, () => jobCardsApi.start(card.id), 'Work started');
  const logWork = useJobCardAction(card.id, (v: Parameters<typeof jobCardsApi.logWork>[1]) => jobCardsApi.logWork(card.id, v), 'Work logged');
  const addPart = useJobCardAction(card.id, (v: Parameters<typeof jobCardsApi.addPart>[1]) => jobCardsApi.addPart(card.id, v), 'Part added');
  const addLabour = useJobCardAction(card.id, (v: Parameters<typeof jobCardsApi.addLabour>[1]) => jobCardsApi.addLabour(card.id, v), 'Labour added');
  const complete = useJobCardAction(card.id, () => jobCardsApi.complete(card.id), 'Job completed and added to the service history');

  const canComplete = (advisor || mechanic) && (status === 'IN_PROGRESS' || status === 'DECLINED');

  return (
    <div className="grid gap-6">
      <PageHeader
        title={
          <span className="flex flex-wrap items-center gap-3">
            Job card · {card.registration} <JobCardStatusBadge status={status} />
          </span>
        }
        description={`${card.customerName} · VIN ${card.vin} · ${formatMileage(card.mileage)} at check-in`}
        actions={
          <>
            {mechanic && (status === 'APPROVED' || status === 'PARTIALLY_APPROVED') && (
              <Button disabled={start.isPending} onClick={() => start.mutate(undefined)}>
                Start work
              </Button>
            )}
            {canComplete && (
              <Button disabled={complete.isPending} onClick={() => complete.mutate(undefined)}>
                Complete job
              </Button>
            )}
            {!mechanic && (
              <Button asChild variant="outline">
                <Link to={`/vehicles/${card.vehicleId}`}>Vehicle history</Link>
              </Button>
            )}
          </>
        }
      />

      <Card>
        <CardContent className="pt-6">
          <JobCardTimeline card={card} />
        </CardContent>
      </Card>

      {customer && status === 'AWAITING_APPROVAL' && (
        <Alert variant="info">
          Please approve or decline each recommended item below. Work starts once you've decided on all of them.
        </Alert>
      )}

      {/* ---- Inspection findings ---- */}
      <Card>
        <CardHeader>
          <CardTitle>Inspection findings</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">
          {card.findings.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nothing recorded yet.</p>
          ) : (
            <ul className="grid gap-2">
              {card.findings.map((f) => (
                <li key={f.id} className="flex flex-wrap items-center gap-2 text-sm">
                  <SeverityBadge severity={f.severity} />
                  <span>{f.description}</span>
                  <span className="text-xs text-muted-foreground">
                    — {f.recordedBy}, {formatDateTime(f.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {(advisor || mechanic) && inspecting && <FindingForm onSubmit={(v) => addFinding.mutateAsync(v)} />}
        </CardContent>
      </Card>

      {/* ---- Recommendations and approval ---- */}
      <Card>
        <CardHeader>
          <CardTitle>Recommended work</CardTitle>
          <CardDescription>
            Estimates are parts plus labour at {money(card.labourRate)} per hour.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {card.recommendations.length === 0 ? (
            <p className="text-sm text-muted-foreground">No work recommended.</p>
          ) : (
            <ul className="grid gap-3">
              {card.recommendations.map((item) => (
                <RecommendationRow
                  key={item.id}
                  item={item}
                  money={money}
                  canDecide={(customer || advisor) && status === 'AWAITING_APPROVAL' && item.approvalStatus === 'PENDING'}
                  deciding={decide.isPending}
                  onDecide={(decision) => decide.mutate({ itemId: item.id, decision })}
                />
              ))}
            </ul>
          )}
          <div className="flex flex-wrap justify-end gap-6 border-t pt-3 text-sm">
            <span>
              Estimated total <strong>{money(card.estimatedTotal)}</strong>
            </span>
            <span>
              Approved <strong>{money(card.approvedEstimate)}</strong>
            </span>
          </div>
          {advisor && inspecting && (
            <RecommendationForm labourRate={card.labourRate} onSubmit={(v) => addRecommendation.mutateAsync(v)} />
          )}
          {advisor && status === 'INSPECTED' && (
            <div className="flex flex-wrap items-center gap-3">
              <Button disabled={submit.isPending} onClick={() => submit.mutate(undefined)}>
                {card.recommendations.length === 0 ? 'Finish inspection (no work needed)' : 'Send to customer for approval'}
              </Button>
              <span className="text-sm text-muted-foreground">
                Findings and recommendations can't be changed after this.
              </span>
            </div>
          )}
        </CardContent>
      </Card>

      {/* ---- Work ---- */}
      {workPhase && (
        <Card>
          <CardHeader>
            <CardTitle>Work</CardTitle>
            <CardDescription>
              {status === 'IN_PROGRESS'
                ? 'Log the work done for every approved item before completing the job.'
                : status === 'COMPLETED'
                  ? approved.length === 0
                    ? 'Inspection only: no work was approved.'
                    : 'The work carried out.'
                  : 'Work starts when a mechanic picks up the job.'}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-6">
            {approved.map((item) => (
              <WorkSummary key={item.id} card={card} item={item} money={money} />
            ))}
            {mechanic && status === 'IN_PROGRESS' && (
              <WorkForms
                items={approved}
                labourRate={card.labourRate}
                onWork={(v) => logWork.mutateAsync(v)}
                onPart={(v) => addPart.mutateAsync(v)}
                onLabour={(v) => addLabour.mutateAsync(v)}
              />
            )}
            {status === 'COMPLETED' && (
              <dl className="grid justify-end gap-1 border-t pt-3 text-sm sm:grid-cols-[auto_8rem]">
                <dt className="text-muted-foreground">Parts</dt>
                <dd className="text-right">{money(card.partsTotal)}</dd>
                <dt className="text-muted-foreground">Labour</dt>
                <dd className="text-right">{money(card.labourTotal)}</dd>
                <dt className="font-semibold">Total</dt>
                <dd className="text-right font-semibold">{money(card.grandTotal)}</dd>
              </dl>
            )}
          </CardContent>
        </Card>
      )}

    </div>
  );
}

function RecommendationRow({
  item,
  money,
  canDecide,
  deciding,
  onDecide,
}: {
  item: Recommendation;
  money: (amount?: number | null) => string;
  canDecide: boolean;
  deciding: boolean;
  onDecide: (decision: 'approve' | 'decline') => void;
}) {
  return (
    <li className="grid gap-2 rounded-lg border p-4 sm:grid-cols-[1fr_auto] sm:items-center">
      <div className="grid gap-1">
        <span className="font-medium">{item.description}</span>
        <span className="text-sm text-muted-foreground">
          Parts {money(item.estimatedPartsCost)} · Labour {item.estimatedLabourHours} h ({money(item.estimatedLabourCost)})
          · <strong className="text-foreground">{money(item.estimatedTotal)}</strong>
        </span>
        <span className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <ApprovalBadge status={item.approvalStatus} />
          {item.decidedBy && <span>by {item.decidedBy}</span>}
        </span>
      </div>
      {canDecide && (
        <div className="flex gap-2">
          <Button size="sm" disabled={deciding} onClick={() => onDecide('approve')} aria-label={`Approve ${item.description}`}>
            Approve
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={deciding}
            onClick={() => onDecide('decline')}
            aria-label={`Decline ${item.description}`}
          >
            Decline
          </Button>
        </div>
      )}
    </li>
  );
}

function WorkSummary({
  card,
  item,
  money,
}: {
  card: JobCard;
  item: Recommendation;
  money: (amount?: number | null) => string;
}) {
  const logs = card.workLogs.filter((w) => w.recommendationId === item.id);
  const parts = card.parts.filter((p) => p.recommendationId === item.id);
  const labour = card.labour.filter((l) => l.recommendationId === item.id);
  return (
    <section aria-label={item.description} className="grid gap-2 rounded-lg border p-4 text-sm">
      <h3 className="font-medium">{item.description}</h3>
      {logs.length === 0 && parts.length === 0 && labour.length === 0 ? (
        <p className="text-muted-foreground">Nothing logged yet.</p>
      ) : (
        <ul className="grid gap-1">
          {logs.map((w) => (
            <li key={w.id}>
              ✔ {w.description} <span className="text-muted-foreground">— {w.mechanic}</span>
            </li>
          ))}
          {parts.map((p) => (
            <li key={p.id}>
              {p.quantity} × {p.name} ({p.partNumber}) <span className="text-muted-foreground">— {money(p.lineTotal)}</span>
            </li>
          ))}
          {labour.map((l) => (
            <li key={l.id}>
              {l.hours} h by {l.mechanic} <span className="text-muted-foreground">— {money(l.cost)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
