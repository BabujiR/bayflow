import type { AppNotification } from '@/api/types';
import { useLocationLookup } from '@/features/locations/use-locations';
import { formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

/** Only paths inside this app are followed; anything else is shown without a link. */
export function safeLink(link: string | null): string | null {
  return link && link.startsWith('/') && !link.startsWith('//') && !link.includes('\\') ? link : null;
}

/** One notification: title, body, when (in its location's time) and where. Plain text only. */
export function NotificationItem({ notification, compact = false }: { notification: AppNotification; compact?: boolean }) {
  const locations = useLocationLookup();
  const zone = locations.zoneOf(notification.locationId);
  return (
    <div className="flex w-full items-start gap-3 text-left">
      <span
        aria-hidden
        className={cn('mt-1.5 size-2 shrink-0 rounded-full', notification.read ? 'bg-transparent' : 'bg-primary')}
      />
      <span className="grid min-w-0 flex-1 gap-0.5">
        <span className={cn('text-sm', notification.read ? 'font-normal' : 'font-semibold')}>
          {notification.title}
          {!notification.read && <span className="sr-only"> (unread)</span>}
        </span>
        <span className={cn('text-sm text-muted-foreground', compact && 'line-clamp-2')}>{notification.body}</span>
        <span className="text-xs text-muted-foreground">
          {formatDateTime(notification.createdAt, zone)}
          {locations.hasSeveral && notification.locationId && ` · ${locations.nameOf(notification.locationId)}`}
        </span>
      </span>
    </div>
  );
}
