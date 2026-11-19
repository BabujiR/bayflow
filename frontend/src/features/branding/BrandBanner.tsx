import { cn } from '@/lib/utils';

import { useBranding } from './use-branding';

/**
 * The workshop's announcement while it is live: full for customers and on the sign-in page,
 * slim for staff. Plain text only (React escapes it).
 */
export function BrandBanner({ variant = 'full' }: { variant?: 'full' | 'slim' }) {
  const { banner } = useBranding().branding;
  if (!banner) {
    return null;
  }
  const important = banner.style === 'IMPORTANT';
  return (
    <aside
      aria-label="Workshop announcement"
      data-testid="workshop-banner"
      data-style={banner.style}
      data-variant={variant}
      className={cn(
        'border-b text-center',
        variant === 'full' ? 'px-4 py-2.5 text-sm' : 'px-4 py-1 text-xs',
        important
          ? 'border-warning/50 bg-warning/20 font-medium text-foreground'
          : 'border-primary/20 bg-primary/10 text-foreground',
      )}
    >
      <p className="mx-auto max-w-screen-2xl break-words">{banner.text}</p>
    </aside>
  );
}
