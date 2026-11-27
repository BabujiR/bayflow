import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { estimateApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { CustomerEstimate, CustomerEstimateItem } from '@/api/types';
import { ApprovalBadge, SeverityBadge } from '@/components/common/StatusBadge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { BookingShell, PublicQueryState } from '@/features/booking/booking-ui';
import { cn } from '@/lib/utils';
import { formatDateTime, formatMoney } from '@/lib/format';

import { SignaturePad } from './SignaturePad';

const estimateKey = (token: string) => ['estimate', token];

/**
 * The customer's estimate, from the link the shop e-mailed (`/estimate?token=…`): no sign-in.
 * They approve or decline each recommended item, type their name and sign; the shop is told at
 * once and starts on what they approved.
 */
export function EstimateApprovalPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const estimate = useQuery({
    queryKey: estimateKey(token),
    queryFn: () => estimateApi.view(token),
    enabled: token !== '',
    retry: (count, error) => !(error instanceof ApiError && error.isClientError) && count < 2,
  });

  return (
    <BookingShell shopName={estimate.data?.shopName ?? undefined}>
      {token === '' ? (
        <Alert>This link is missing its code. Use the link in your e-mail from the shop.</Alert>
      ) : (
        <PublicQueryState query={estimate}>
          {(e) => <EstimateView token={token} estimate={e} />}
        </PublicQueryState>
      )}
    </BookingShell>
  );
}

function EstimateView({ token, estimate: e }: { token: string; estimate: CustomerEstimate }) {
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<string, boolean>>({});
  const [name, setName] = useState('');
  const [signature, setSignature] = useState<string | null>(null);
  const [tried, setTried] = useState(false);
  const money = (amount: number) => formatMoney(amount, e.currency);
  const pending = e.items.filter((i) => i.status === 'PENDING');
  const decided = pending.every((i) => answers[i.id] !== undefined);
  const approvedTotal = e.items
    .filter((i) => (i.status === 'PENDING' ? answers[i.id] === true : i.status === 'APPROVED'))
    .reduce((sum, i) => sum + i.price, 0);

  const decide = useMutation({
    mutationFn: () =>
      estimateApi.decide({
        token,
        decisions: pending.map((i) => ({ itemId: i.id, approve: answers[i.id] })),
        signerName: name.trim(),
        signature: signature ?? '',
      }),
    onSuccess: (result) => queryClient.setQueryData(estimateKey(token), result),
  });

  const confirm = () => {
    setTried(true);
    if (decided && name.trim().length >= 2 && signature) {
      decide.mutate();
    }
  };

  return (
    <div className="grid gap-6">
      <div className="grid gap-1">
        <h1 className="text-2xl font-bold tracking-tight">Your estimate</h1>
        <p className="text-muted-foreground">
          {e.firstName ? `Hi ${e.firstName}, here` : 'Here'}'s what we recommend for your {e.vehicle}
          {e.locationName ? ` at ${e.locationName}` : ''}.
        </p>
      </div>

      {e.decidedAt && (
        <Alert variant="info">
          Thank you{e.signerName ? `, ${e.signerName}` : ''}. You signed this estimate on{' '}
          {formatDateTime(e.decidedAt)}, and the shop has been told. They'll start on what you approved.
        </Alert>
      )}
      {!e.canDecide && !e.decidedAt && (
        <Alert variant="info">
          {e.status === 'AWAITING_APPROVAL'
            ? 'This estimate is being answered with the shop.'
            : 'This estimate has already been answered.'}
          {e.shopPhone ? ` Questions? Call ${e.shopPhone}.` : ''}
        </Alert>
      )}

      {e.findings.length > 0 && (
        <Card>
          <CardContent className="grid gap-3 pt-6">
            <h2 className="font-semibold">What we found</h2>
            <ul className="grid gap-2">
              {e.findings.map((f, index) => (
                <li key={index} className="flex items-start justify-between gap-3 text-sm">
                  <span>{f.description}</span>
                  <SeverityBadge severity={f.severity} />
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="grid gap-4 pt-6">
          <h2 className="font-semibold">Recommended work</h2>
          <ul className="grid gap-3">
            {e.items.map((item) => (
              <ItemRow
                key={item.id}
                item={item}
                money={money}
                answer={answers[item.id]}
                canDecide={e.canDecide && item.status === 'PENDING'}
                missing={tried && e.canDecide && item.status === 'PENDING' && answers[item.id] === undefined}
                onAnswer={(approve) => setAnswers((current) => ({ ...current, [item.id]: approve }))}
              />
            ))}
          </ul>
          <dl className="grid gap-1 border-t pt-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Estimate total</dt>
              <dd>{money(e.total)}</dd>
            </div>
            <div className="flex justify-between font-semibold">
              <dt>{e.canDecide ? "You're approving" : 'Approved'}</dt>
              <dd data-testid="approved-total">{money(approvedTotal)}</dd>
            </div>
          </dl>
          <p className="text-xs text-muted-foreground">
            Prices are estimates (parts and labour, before tax). The shop will contact you if anything
            changes.
          </p>
        </CardContent>
      </Card>

      {e.canDecide && (
        <Card>
          <CardContent className="grid gap-4 pt-6">
            <h2 className="font-semibold">Sign to confirm</h2>
            <Field
              label="Your full name"
              error={tried && name.trim().length < 2 ? 'Type your full name' : undefined}
            >
              <Input
                value={name}
                autoComplete="name"
                maxLength={80}
                onChange={(ev) => setName(ev.target.value)}
              />
            </Field>
            <div className="grid gap-1.5">
              <SignaturePad onChange={setSignature} label="Sign here" />
              {tried && !signature && (
                <p role="alert" className="text-sm text-destructive">
                  Sign in the box
                </p>
              )}
            </div>
            {tried && !decided && <Alert>Approve or decline each item above, then confirm.</Alert>}
            {decide.isError && <Alert>{errorMessage(decide.error)}</Alert>}
            <p className="text-xs text-muted-foreground">
              By signing you authorize the shop to do the work you approved, for about {money(approvedTotal)}.
            </p>
            <Button size="lg" disabled={decide.isPending} onClick={confirm}>
              Confirm and sign
            </Button>
          </CardContent>
        </Card>
      )}

      <p className="text-center text-xs text-muted-foreground">
        {e.shopPhone ? `Questions? Call ${e.shopPhone}. ` : ''}
        {e.canDecide ? `This link works until ${formatDateTime(e.expiresAt)}.` : ''}
      </p>
    </div>
  );
}

function ItemRow({
  item,
  money,
  answer,
  canDecide,
  missing,
  onAnswer,
}: {
  item: CustomerEstimateItem;
  money: (amount: number) => string;
  answer: boolean | undefined;
  canDecide: boolean;
  missing: boolean;
  onAnswer: (approve: boolean) => void;
}) {
  return (
    <li
      className={cn('grid gap-2 rounded-lg border p-3', missing && 'border-destructive')}
      data-testid="estimate-item"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="font-medium">{item.description}</span>
        <span className="whitespace-nowrap font-semibold">{money(item.price)}</span>
      </div>
      {canDecide ? (
        <div className="flex gap-2" role="group" aria-label={`Your answer for ${item.description}`}>
          <Button
            type="button"
            size="sm"
            variant={answer === true ? 'default' : 'outline'}
            aria-pressed={answer === true}
            onClick={() => onAnswer(true)}
          >
            Approve
          </Button>
          <Button
            type="button"
            size="sm"
            variant={answer === false ? 'secondary' : 'outline'}
            aria-pressed={answer === false}
            onClick={() => onAnswer(false)}
          >
            Decline
          </Button>
        </div>
      ) : (
        <div>
          <ApprovalBadge status={item.status} />
        </div>
      )}
    </li>
  );
}
