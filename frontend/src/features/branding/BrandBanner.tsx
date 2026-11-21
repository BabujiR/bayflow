import { type ReactNode, useId, useState } from 'react';

import type { PublicAnnouncement } from '@/api/types';
import { cn } from '@/lib/utils';

import { useBranding } from './use-branding';

/**
 * The workshop's live announcements as one line: the most important one, and "N more" to open
 * the rest (option D). Full size for customers and the sign-in page, slim for staff. Plain text
 * only (React escapes it).
 */
export function BrandBanner({ variant = 'full' }: { variant?: 'full' | 'slim' }) {
  const { announcements } = useBranding().branding;
  const [expanded, setExpanded] = useState(false);
  const moreId = useId();
  if (announcements.length === 0) {
    return null;
  }
  const [first, ...rest] = announcements;
  const slim = variant === 'slim';

  return (
    <aside
      aria-label="Workshop announcements"
      data-testid="workshop-banner"
      data-variant={variant}
      data-count={announcements.length}
    >
      <Line announcement={first} slim={slim}>
        {rest.length > 0 && (
          <button
            type="button"
            aria-expanded={expanded}
            aria-controls={moreId}
            onClick={() => setExpanded((open) => !open)}
            className={cn(
              'inline-flex shrink-0 items-center gap-1 rounded-full border bg-background font-semibold',
              'hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              slim ? 'h-6 px-2 text-xs' : 'h-8 px-3 text-sm',
            )}
          >
            {expanded ? 'Show less' : `${rest.length} more`}
            <svg
              aria-hidden
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className={cn('size-3.5 transition-transform', expanded && 'rotate-180')}
            >
              <path d="m6 9 6 6 6-6" />
            </svg>
          </button>
        )}
      </Line>
      {rest.length > 0 && (
        <ul id={moreId} hidden={!expanded} className="m-0 list-none p-0">
          {rest.map((announcement) => (
            <li key={announcement.id}>
              <Line announcement={announcement} slim={slim} />
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}

function Line({
  announcement,
  slim,
  children,
}: {
  announcement: PublicAnnouncement;
  slim: boolean;
  children?: ReactNode;
}) {
  const important = announcement.style === 'IMPORTANT';
  return (
    <div
      data-testid="announcement"
      data-style={announcement.style}
      className={cn(
        'border-b',
        important ? 'border-warning/50 bg-warning/20' : 'border-primary/20 bg-primary/10',
      )}
    >
      <div
        className={cn(
          'mx-auto flex max-w-screen-2xl items-center gap-3 px-4',
          slim ? 'min-h-8 py-1 text-xs' : 'min-h-11 py-2 text-sm',
        )}
      >
        <p className={cn('m-0 min-w-0 flex-1 break-words text-foreground', important && 'font-semibold')}>
          {announcement.text}
        </p>
        {children}
      </div>
    </div>
  );
}
