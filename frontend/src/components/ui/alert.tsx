import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

/** A message box for form-level errors and notices. */
export function Alert({
  className,
  variant = 'error',
  ...props
}: ComponentProps<'div'> & { variant?: 'error' | 'info' }) {
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={cn(
        'rounded-md border px-4 py-3 text-sm',
        variant === 'error'
          ? 'border-destructive/40 bg-destructive/10 text-destructive'
          : 'border-primary/30 bg-primary/5 text-foreground',
        className,
      )}
      {...props}
    />
  );
}
