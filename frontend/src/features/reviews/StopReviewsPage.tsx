import { useMutation } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { reviewRequestsApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { BookingShell } from '@/features/booking/booking-ui';

/**
 * The stop link in a review request (`/reviews/stop?token=…`): no sign-in. The customer confirms
 * with a click (mail scanners open links on their own; they don't press buttons).
 */
export function StopReviewsPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const stop = useMutation({ mutationFn: () => reviewRequestsApi.stop(token) });

  return (
    <BookingShell shopName={stop.data?.shopName ?? undefined}>
      <Card>
        <CardContent className="grid gap-4 pt-6">
          <h1 className="text-xl font-semibold">Review requests</h1>
          {token === '' ? (
            <Alert>This link is missing its code. Use the link in your e-mail from the shop.</Alert>
          ) : stop.isSuccess ? (
            <p role="status">
              Done. {stop.data.shopName ?? 'The shop'} won't e-mail you asking for reviews again. You'll still
              get your invoices and booking e-mails.
            </p>
          ) : (
            <>
              <p className="text-muted-foreground">
                Stop the e-mails asking you to review the shop? Invoices and booking e-mails still come.
              </p>
              {stop.isError && (
                <Alert>
                  {stop.error instanceof ApiError && stop.error.code === 'review-link-invalid'
                    ? "This link isn't valid. Use the link in your latest e-mail from the shop."
                    : errorMessage(stop.error)}
                </Alert>
              )}
              <div>
                <Button disabled={stop.isPending} onClick={() => stop.mutate()}>
                  Stop review requests
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </BookingShell>
  );
}
