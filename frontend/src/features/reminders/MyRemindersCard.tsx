import { useQuery, useQueryClient } from '@tanstack/react-query';

import { remindersApi } from '@/api/endpoints';
import { Card, CardContent } from '@/components/ui/card';
import { useAction } from '@/lib/use-action';

const KEY = ['service-reminders', 'me'] as const;

/** A customer's switch for maintenance reminders (on the Notifications page). */
export function MyRemindersCard() {
  const queryClient = useQueryClient();
  const mine = useQuery({ queryKey: KEY, queryFn: remindersApi.mine });
  const save = useAction((enabled: boolean) => remindersApi.setMine(enabled), {
    success: (r) => (r.enabled ? 'Service reminders turned on' : 'Service reminders turned off'),
    onSuccess: (r) => queryClient.setQueryData(KEY, r),
  });
  if (!mine.data) {
    return null;
  }
  return (
    <Card className="mt-6">
      <CardContent className="flex items-start gap-3 pt-6">
        <input
          id="service-reminders"
          type="checkbox"
          className="mt-1 size-4"
          checked={mine.data.enabled}
          disabled={save.isPending}
          onChange={(e) => save.mutate(e.target.checked)}
        />
        <label htmlFor="service-reminders" className="grid gap-0.5 text-sm">
          <span className="font-medium">Service reminders</span>
          <span className="text-muted-foreground">
            Tell me when my vehicle is due for maintenance, like an oil change or tire rotation.
          </span>
        </label>
      </CardContent>
    </Card>
  );
}
