import { cn } from '@/lib/utils';

export const TAGLINE = 'The smarter way to run your auto workshop.';

/** The Bayflow mark and wordmark; the tagline shows on wide screens when requested. */
export function Logo({ withTagline = false, className }: { withTagline?: boolean; className?: string }) {
  return (
    <span className={cn('flex items-center gap-2.5', className)}>
      <svg aria-hidden viewBox="0 0 32 32" className="size-8 shrink-0">
        <rect width="32" height="32" rx="8" className="fill-primary" />
        <path d="M7 22V12l9-5 9 5v10" fill="none" stroke="white" strokeWidth="2.4" strokeLinejoin="round" />
        <path d="M10 22c3-4 9-4 12 0" fill="none" stroke="#5eead4" strokeWidth="2.4" strokeLinecap="round" />
      </svg>
      <span className="flex flex-col leading-tight">
        <span className="text-lg font-bold tracking-tight">Bayflow</span>
        {withTagline && <span className="hidden text-xs text-muted-foreground lg:block">{TAGLINE}</span>}
      </span>
    </span>
  );
}
