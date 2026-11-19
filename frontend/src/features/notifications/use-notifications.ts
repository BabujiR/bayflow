import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

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

/** A page of the user's notifications, newest first. */
export function useNotifications(page = 0, size = 20, enabled = true) {
  const { status } = useAuth();
  return useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list', page, size],
    queryFn: () => notificationsApi.list(page, size),
    enabled: enabled && status === 'authenticated',
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** Mark one or all read; the lists and the bell update straight away. */
export function useMarkRead() {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY });
  const one = useMutation({
    mutationFn: notificationsApi.markRead,
    onMutate: async (id: string) => {
      queryClient.setQueriesData<PageResponse<AppNotification>>(
        { queryKey: [...NOTIFICATIONS_KEY, 'list'] },
        (page) => page && { ...page, content: page.content.map((n) => (n.id === id ? { ...n, read: true } : n)) },
      );
    },
    onSettled: refresh,
  });
  const all = useMutation({ mutationFn: notificationsApi.markAllRead, onSettled: refresh });
  return { one, all };
}
