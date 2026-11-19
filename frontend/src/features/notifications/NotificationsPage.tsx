import { useState } from 'react';
import { useNavigate } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

import { NotificationItem, safeLink } from './NotificationItem';
import { useMarkRead, useNotifications, useUnreadCount } from './use-notifications';

/** Every notification of the signed-in user, newest first. */
export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const list = useNotifications(page, 20);
  const unread = useUnreadCount();
  const { one, all } = useMarkRead();
  const navigate = useNavigate();

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Updates on your bookings and job cards. Kept for 90 days."
        actions={
          <Button variant="outline" disabled={!unread.data || all.isPending} onClick={() => all.mutate()}>
            Mark all read
          </Button>
        }
      />
      <QueryState query={list}>
        {(data) =>
          data.content.length === 0 ? (
            <EmptyState title="No notifications yet" description="Updates on bookings and job cards appear here." />
          ) : (
            <>
              <Card className="divide-y" role="list" aria-label="Notifications">
                {data.content.map((n) => {
                  const target = safeLink(n.link);
                  return (
                    <div key={n.id} role="listitem">
                      <button
                        type="button"
                        className="w-full px-4 py-3 text-left hover:bg-muted focus-visible:bg-muted focus-visible:outline-none"
                        data-testid="notification-item"
                        onClick={() => {
                          if (!n.read) {
                            one.mutate(n.id);
                          }
                          if (target) {
                            navigate(target);
                          }
                        }}
                      >
                        <NotificationItem notification={n} />
                      </button>
                    </div>
                  );
                })}
              </Card>
              <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
            </>
          )
        }
      </QueryState>
    </>
  );
}
