import { useState } from 'react';
import { useNavigate } from 'react-router';

import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

import { MyRemindersCard } from '@/features/reminders/MyRemindersCard';

import { NotificationItem, safeLink } from './NotificationItem';
import { useMarkRead, useNotifications, useUnreadCount } from './use-notifications';

/**
 * The signed-in user's notifications, newest first. All of them are unread: opening one (or
 * "Mark all read") deletes it.
 */
export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const list = useNotifications(page, 20);
  const unread = useUnreadCount();
  const { one, all } = useMarkRead();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const customer = hasRole('CUSTOMER') && !hasRole('SERVICE_ADVISOR', 'MECHANIC', 'ADMIN');

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Updates on your bookings and job cards. Opening one removes it."
        actions={
          <Button variant="outline" disabled={!unread.data || all.isPending} onClick={() => all.mutate()}>
            Mark all read
          </Button>
        }
      />
      <QueryState query={list}>
        {(data) =>
          data.content.length === 0 ? (
            <EmptyState
              title="You're all caught up"
              description="New updates on your bookings and job cards appear here."
            />
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
                          one.mutate(n.id);
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
      {customer && <MyRemindersCard />}
    </>
  );
}
