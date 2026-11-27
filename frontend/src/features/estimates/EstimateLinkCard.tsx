import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { jobCardsApi } from '@/api/endpoints';
import type { EstimateLinkStatus, JobCardStatus } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { formatDateTime } from '@/lib/format';
import { useAction } from '@/lib/use-action';

/** By status too: submitting e-mails the link, so a new status means a fresh look. */
const linkKey = (jobCardId: string, status: JobCardStatus) => [
  'job-card',
  jobCardId,
  'estimate-link',
  status,
];

/**
 * Advisors: the link the customer approves the estimate from (e-mailed on submit). Who it went
 * to, whether they opened it, reminders, and their signature once they signed; send it again
 * (or to another address) while the estimate waits.
 */
export function EstimateLinkCard({
  jobCardId,
  status,
  zone,
}: {
  jobCardId: string;
  status: JobCardStatus;
  zone?: string;
}) {
  const queryClient = useQueryClient();
  const waiting = status === 'AWAITING_APPROVAL';
  const link = useQuery({
    queryKey: linkKey(jobCardId, status),
    queryFn: () => jobCardsApi.estimateLink(jobCardId),
  });
  const [to, setTo] = useState('');
  const [other, setOther] = useState(false);
  const send = useAction((address: string | undefined) => jobCardsApi.sendEstimateLink(jobCardId, address), {
    success: (result) => `Approval link e-mailed to ${result.sentTo}`,
    onSuccess: (result) => {
      queryClient.setQueryData(linkKey(jobCardId, status), result);
      setOther(false);
      setTo('');
    },
  });

  if (link.isPending || (!waiting && !link.data)) {
    return null;
  }
  const sent = link.data;
  const when = (iso: string | null) => (iso ? formatDateTime(iso, zone) : '');

  return (
    <Card>
      <CardHeader>
        <CardTitle>Customer approval link</CardTitle>
        <CardDescription>
          The customer opens it from their e-mail, approves or declines each item and signs, with no account.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {sent ? (
          <LinkSummary jobCardId={jobCardId} link={sent} when={when} />
        ) : (
          <p className="text-sm text-muted-foreground">No link sent yet.</p>
        )}
        {waiting && !sent?.signed && (
          <div className="grid gap-3">
            {other && (
              <Field label="Send to">
                <Input
                  type="email"
                  value={to}
                  placeholder="name@example.com"
                  onChange={(e) => setTo(e.target.value)}
                />
              </Field>
            )}
            <div className="flex flex-wrap gap-2">
              <Button
                disabled={send.isPending || (other && to.trim() === '')}
                onClick={() => send.mutate(other ? to.trim() : undefined)}
              >
                {sent ? 'Send again' : 'E-mail approval link'}
              </Button>
              <Button variant="ghost" onClick={() => setOther(!other)}>
                {other ? "Use the customer's address" : 'Send to another address'}
              </Button>
            </div>
            {sent && (
              <p className="text-xs text-muted-foreground">Sending again replaces the earlier link.</p>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function LinkSummary({
  jobCardId,
  link,
  when,
}: {
  jobCardId: string;
  link: EstimateLinkStatus;
  when: (iso: string | null) => string;
}) {
  const rows: [string, string][] = [
    ['Sent to', link.sentTo],
    ['Sent', `${when(link.sentAt)} by ${link.sentBy}`],
    ['Opened', link.firstViewedAt ? when(link.firstViewedAt) : 'Not yet'],
  ];
  if (!link.signed) {
    rows.push([
      'Reminders',
      `${link.remindersSent} sent${link.nextReminderAt ? `, next ${when(link.nextReminderAt)}` : ''}`,
    ]);
    rows.push(['Link', link.open ? `Works until ${when(link.expiresAt)}` : 'Expired: send it again']);
  } else {
    rows.push(['Signed', `${when(link.decidedAt)} by ${link.signerName ?? ''}`]);
  }
  return (
    <div className="grid gap-3">
      <dl className="grid gap-1 rounded-lg bg-muted/50 p-3 text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-3">
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="text-right font-medium">{value}</dd>
          </div>
        ))}
      </dl>
      {link.signed && <Signature jobCardId={jobCardId} signer={link.signerName} />}
    </div>
  );
}

/** The signature image: fetched with the staff member's token, shown from memory. */
function Signature({ jobCardId, signer }: { jobCardId: string; signer: string | null }) {
  const image = useQuery({
    queryKey: ['job-card', jobCardId, 'estimate-signature'],
    queryFn: () => jobCardsApi.estimateSignature(jobCardId),
    staleTime: Infinity,
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!image.data) {
      return;
    }
    const objectUrl = URL.createObjectURL(image.data);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [image.data]);
  if (!url) {
    return null;
  }
  return (
    <img
      src={url}
      alt={`Signature of ${signer ?? 'the customer'}`}
      className="h-24 w-auto max-w-full rounded-md border bg-white p-1"
    />
  );
}
