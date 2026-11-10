import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

/** A styled native select: accessible and keyboard-friendly on every device. */
export function Select({ className, ...props }: ComponentProps<'select'>) {
  return (
    <select
      className={cn(
        'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-xs',
        'disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive',
        className,
      )}
      {...props}
    />
  );
}
