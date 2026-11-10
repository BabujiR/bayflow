import type { UseQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';

import { errorMessage } from '@/api/errors';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

/**
 * Loading skeleton, readable error with retry, or the content once the query has data.
 */
export function QueryState<T>({
  query,
  children,
  skeleton,
}: {
  query: UseQueryResult<T>;
  children: (data: T) => ReactNode;
  skeleton?: ReactNode;
}) {
  if (query.isPending) {
    return (
      <div aria-busy="true" aria-label="Loading">
        {skeleton ?? (
          <div className="grid gap-3">
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
          </div>
        )}
      </div>
    );
  }
  if (query.isError) {
    return (
      <Alert className="flex flex-wrap items-center justify-between gap-3">
        <span>{errorMessage(query.error)}</span>
        <Button size="sm" variant="outline" onClick={() => void query.refetch()}>
          Try again
        </Button>
      </Alert>
    );
  }
  return <>{children(query.data)}</>;
}
