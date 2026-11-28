import { useQuery, useQueryClient } from '@tanstack/react-query';

import { reviewRequestsApi } from '@/api/endpoints';
import type { ReviewRequestStatus } from '@/api/types';
import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/format';
import { useAction } from '@/lib/use-action';

const key = (invoiceId: string) => ['review-request', invoiceId];

function describe(r: ReviewRequestStatus, zone?: string): string {
  switch (r.status) {
    case 'SCHEDULED':
      return `Google review request scheduled for ${formatDateTime(r.dueAt, zone)}`;
    case 'SENT':
      return `Google review requested on ${formatDateTime(r.sentAt, zone)}${r.sentTo ? ` (${r.sentTo})` : ''}`;
    case 'SKIPPED':
      return `Google review request not sent: ${r.reason ?? 'skipped'}`;
    case 'CANCELLED':
      return `Google review request: ${r.reason ?? 'not sent'}`;
    default:
      return 'No Google review request (they are off at this location)';
  }
}

/** On a paid invoice (staff): its Google review request, with "Send now" and "Don't send". */
export function ReviewRequestLine({ invoiceId, zone }: { invoiceId: string; zone?: string }) {
  const queryClient = useQueryClient();
  const request = useQuery({ queryKey: key(invoiceId), queryFn: () => reviewRequestsApi.status(invoiceId) });
  const done = (r: ReviewRequestStatus) => queryClient.setQueryData(key(invoiceId), r);
  const send = useAction(() => reviewRequestsApi.sendNow(invoiceId), {
    success: 'Review request sent',
    onSuccess: done,
  });
  const cancel = useAction(() => reviewRequestsApi.cancel(invoiceId), {
    success: "Review request won't be sent",
    onSuccess: done,
  });

  const r = request.data;
  if (!r) {
    return null;
  }
  const canSend = r.status !== 'SENT';
  return (
    <div
      className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground"
      data-testid="review-request"
    >
      <span>{describe(r, zone)}</span>
      <span className="flex gap-2">
        {r.status === 'SCHEDULED' && (
          <Button
            size="sm"
            variant="ghost"
            disabled={cancel.isPending}
            onClick={() => cancel.mutate(undefined)}
          >
            Don't send
          </Button>
        )}
        {canSend && (
          <Button
            size="sm"
            variant="outline"
            disabled={send.isPending}
            onClick={() => send.mutate(undefined)}
          >
            {r.status === 'SCHEDULED' ? 'Send now' : 'Ask for a review'}
          </Button>
        )}
      </span>
    </div>
  );
}
