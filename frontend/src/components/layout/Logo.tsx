import { PRODUCT_NAME } from '@/lib/brand';
import { cn } from '@/lib/utils';

export const TAGLINE = 'The smarter way to run your auto workshop.';

/**
 * The product's mark and wordmark; the tagline shows on wide screens when requested. compact:
 * the mark only (collapsed sidebar).
 */
export function Logo({
  withTagline = false,
  compact = false,
  className,
}: {
  withTagline?: boolean;
  compact?: boolean;
  className?: string;
}) {
  return (
    <span className={cn('flex items-center gap-2.5', className)}>
      {/* The R with a light: the same mark as public/favicon.svg. */}
      <svg aria-hidden viewBox="0 0 64 64" className="size-8 shrink-0">
        <rect width="64" height="64" rx="16" fill="#2b3bd1" />
        <path
          d="M20 50V14h14a10 10 0 0 1 0 20H20"
          fill="none"
          stroke="white"
          strokeWidth="6"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <path d="M30 34 L46 50" stroke="white" strokeWidth="6" strokeLinecap="round" />
        <circle cx="47" cy="17" r="7.5" fill="#5eead4" opacity="0.25" />
        <circle cx="47" cy="17" r="4.5" fill="#5eead4" />
      </svg>
      <span className={cn('flex flex-col leading-tight', compact && 'sr-only')}>
        <span className="text-lg font-bold tracking-tight">
          {PRODUCT_NAME.startsWith('Ramp') ? (
            <>
              Ramp<span className="text-primary dark:text-[#5eead4]">{PRODUCT_NAME.slice(4)}</span>
            </>
          ) : (
            PRODUCT_NAME
          )}
        </span>
        {withTagline && <span className="hidden text-xs text-muted-foreground lg:block">{TAGLINE}</span>}
      </span>
    </span>
  );
}
