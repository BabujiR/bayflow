import {
  keepPreviousData,
  type QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import { notificationsApi } from '@/api/endpoints';
import type { AppNotification, PageResponse } from '@/api/types';
import { useAuth } from '@/auth/auth-context';

export const NOTIFICATIONS_KEY = ['notifications'] as const;
/** How often the bell asks for new notifications while the tab is open. */
export const POLL_MS = 30_000;

/** The number on the bell: refreshed every 30 seconds and whenever the tab comes back into view. */
export function useUnreadCount() {
  const { status } = useAuth();
  return useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'unread-count'],
    queryFn: notificationsApi.unreadCount,
    enabled: status === 'authenticated',
    refetchInterval: POLL_MS,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: 'always',
    staleTime: 0,
    // A notification-service outage just hides the number; nothing else depends on it.
    retry: false,
    select: (data) => data.count,
  });
}

/** A page of the user's notifications (all unread), newest first. */
export function useNotifications(page = 0, size = 20) {
  const { status } = useAuth();
  return useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list', page, size],
    queryFn: () => notificationsApi.list(page, size),
    enabled: status === 'authenticated',
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

type Page = PageResponse<AppNotification>;

/** Takes notifications out of every cached list and lowers the count, before the server answers. */
async function removeFromCache(queryClient: QueryClient, gone: (n: AppNotification) => boolean) {
  await queryClient.cancelQueries({ queryKey: NOTIFICATIONS_KEY });
  let removed = 0;
  queryClient.setQueriesData<Page>({ queryKey: [...NOTIFICATIONS_KEY, 'list'] }, (page) => {
    if (!page) {
      return page;
    }
    const content = page.content.filter((n) => !gone(n));
    removed = Math.max(removed, page.content.length - content.length);
    return {
      ...page,
      content,
      totalElements: Math.max(0, page.totalElements - (page.content.length - content.length)),
    };
  });
  queryClient.setQueryData<{ count: number }>([...NOTIFICATIONS_KEY, 'unread-count'], (data) =>
    data ? { count: Math.max(0, data.count - removed) } : data,
  );
}

/**
 * Read one or all. Reading deletes: the notification leaves the bell's panel and the page at
 * once, and the server forgets it.
 */
export function useMarkRead() {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY });
  const one = useMutation({
    mutationFn: notificationsApi.markRead,
    onMutate: (id: string) => removeFromCache(queryClient, (n) => n.id === id),
    onSettled: refresh,
  });
  const all = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onMutate: async () => {
      await removeFromCache(queryClient, () => true);
      queryClient.setQueryData<{ count: number }>([...NOTIFICATIONS_KEY, 'unread-count'], { count: 0 });
    },
    onSettled: refresh,
  });
  return { one, all };
}
