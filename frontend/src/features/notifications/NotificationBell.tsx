import { Link, useNavigate } from 'react-router';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { BellIcon } from '@/components/ui/icons';
import { Spinner } from '@/components/ui/spinner';

import { NotificationItem, safeLink } from './NotificationItem';
import { useMarkRead, useNotifications, useUnreadCount } from './use-notifications';

const PANEL_SIZE = 20;

/** The bell in the header: unread count, and the latest notifications in a panel. */
export function NotificationBell() {
  const unread = useUnreadCount();
  const count = unread.data ?? 0;
  const navigate = useNavigate();
  const { one, all } = useMarkRead();
  const label = count > 0 ? `Notifications, ${count} unread` : 'Notifications';

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={label}
          data-testid="notification-bell"
        >
          <BellIcon className="size-5" />
          {count > 0 && (
            <span
              aria-hidden
              data-testid="notification-count"
              className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold leading-none text-primary-foreground"
            >
              {count > 9 ? '9+' : count}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        aria-label="Notifications"
        className="w-[calc(100vw-2rem)] max-w-sm p-0 sm:w-96"
      >
        <Panel
          onOpen={(id, link, read) => {
            if (!read) {
              one.mutate(id);
            }
            const target = safeLink(link);
            if (target) {
              navigate(target);
            }
          }}
          onMarkAll={() => all.mutate()}
          markingAll={all.isPending}
          hasUnread={count > 0}
        />
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function Panel({
  onOpen,
  onMarkAll,
  markingAll,
  hasUnread,
}: {
  onOpen: (id: string, link: string | null, read: boolean) => void;
  onMarkAll: () => void;
  markingAll: boolean;
  hasUnread: boolean;
}) {
  // Loaded when the panel opens (it is only mounted while open).
  const list = useNotifications(0, PANEL_SIZE);
  return (
    <div className="grid">
      <div className="flex items-center justify-between gap-2 px-3 py-2">
        <h2 className="text-sm font-semibold">Notifications</h2>
        <Button
          variant="link"
          size="sm"
          className="h-auto px-0"
          disabled={!hasUnread || markingAll}
          onClick={(event) => {
            event.preventDefault();
            onMarkAll();
          }}
        >
          Mark all read
        </Button>
      </div>
      <DropdownMenuSeparator className="mx-0 my-0" />
      <div className="max-h-[min(28rem,70vh)] overflow-y-auto p-1">
        {list.isPending ? (
          <div className="flex justify-center p-6">
            <Spinner />
          </div>
        ) : list.isError ? (
          <p className="p-4 text-sm text-muted-foreground">Notifications can't be loaded right now.</p>
        ) : list.data.content.length === 0 ? (
          <p className="p-4 text-sm text-muted-foreground">
            You're all caught up. Updates on bookings and job cards appear here.
          </p>
        ) : (
          list.data.content.map((n) => (
            <DropdownMenuItem
              key={n.id}
              className="items-start px-2 py-2"
              data-testid="notification-item"
              onSelect={() => onOpen(n.id, n.link, n.read)}
            >
              <NotificationItem notification={n} compact />
            </DropdownMenuItem>
          ))
        )}
      </div>
      <DropdownMenuSeparator className="mx-0 my-0" />
      <DropdownMenuItem asChild className="justify-center py-2 text-sm font-medium text-primary">
        <Link to="/notifications">See all notifications</Link>
      </DropdownMenuItem>
    </div>
  );
}
