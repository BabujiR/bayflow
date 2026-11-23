import { useState } from 'react';

import { cn } from '@/lib/utils';

import { useBranding } from './use-branding';

/**
 * The workshop's live announcements, one at a time (the most important first), with ‹ and ›
 * to move between them: no arrows for a single one, only › on the first, only ‹ on the last.
 * Full width for customers and the sign-in page; slim inside the staff header (on small screens,
 * a line of its own under it). Plain text only (React escapes it).
 */
export function BrandBanner({
  variant = 'full',
  className,
}: {
  variant?: 'full' | 'slim';
  className?: string;
}) {
  const { announcements } = useBranding().branding;
  const [index, setIndex] = useState(0);
  if (announcements.length === 0) {
    return null;
  }
  // The list can shrink while shown (an announcement ended): stay within it.
  const current = Math.min(index, announcements.length - 1);
  const announcement = announcements[current];
  const several = announcements.length > 1;
  const important = announcement.style === 'IMPORTANT';
  const slim = variant === 'slim';

  return (
    <aside
      aria-label="Workshop announcements"
      data-testid="workshop-banner"
      data-variant={variant}
      data-count={announcements.length}
      className={cn(
        slim ? 'min-w-0 rounded-full border px-1 text-xs lg:text-sm' : 'border-b text-sm',
        important ? 'border-warning/50 bg-warning/20' : 'border-primary/20 bg-primary/10',
        className,
      )}
    >
      <div
        className={cn(
          'mx-auto flex items-center gap-1',
          slim ? 'min-h-8' : 'min-h-11 max-w-screen-2xl px-2 py-1.5 sm:px-4',
        )}
      >
        {several && (
          <Arrow
            direction="previous"
            hidden={current === 0}
            slim={slim}
            onClick={() => setIndex(current - 1)}
          />
        )}
        <p
          data-testid="announcement"
          data-style={announcement.style}
          aria-live="polite"
          title={slim ? announcement.text : undefined}
          className={cn(
            'm-0 min-w-0 flex-1 text-foreground',
            slim ? 'truncate px-2 lg:text-center' : 'break-words px-1 text-center',
            important && 'font-semibold',
          )}
        >
          {several && (
            <span className="sr-only">
              Announcement {current + 1} of {announcements.length}:{' '}
            </span>
          )}
          {announcement.text}
        </p>
        {several && (
          <Arrow
            direction="next"
            hidden={current === announcements.length - 1}
            slim={slim}
            onClick={() => setIndex(current + 1)}
          />
        )}
      </div>
    </aside>
  );
}

/** ‹ or ›. When there is nothing that way it keeps its space (invisible), so the text stays put. */
function Arrow({
  direction,
  hidden,
  slim,
  onClick,
}: {
  direction: 'previous' | 'next';
  hidden: boolean;
  slim: boolean;
  onClick: () => void;
}) {
  const size = slim ? 'size-7' : 'size-8';
  if (hidden) {
    return <span aria-hidden className={cn('shrink-0', size)} />;
  }
  return (
    <button
      type="button"
      aria-label={direction === 'previous' ? 'Previous announcement' : 'Next announcement'}
      onClick={onClick}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full text-foreground hover:bg-background/70',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        size,
      )}
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-4"
      >
        <path d={direction === 'previous' ? 'm15 18-6-6 6-6' : 'm9 18 6-6-6-6'} />
      </svg>
    </button>
  );
}
