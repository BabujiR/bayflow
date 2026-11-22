import { Logo } from '@/components/layout/Logo';
import { useResolvedTheme } from '@/lib/theme';
import { cn } from '@/lib/utils';

import { useBranding } from './use-branding';

const SIZES = {
  // Header and phone header: one line of controls.
  header: 'h-9 max-w-40',
  // The sidebar's top: its full width (about 208 px), up to 56 px tall.
  sidebar: 'max-h-14 max-w-full',
  // Sign-in page.
  large: 'h-16 max-w-64',
} as const;

/**
 * The workshop's own logo when it has one (its dark-mode logo in dark mode, if uploaded),
 * otherwise Ramplight's. compact: a square mark for narrow places (collapsed sidebar, phone
 * header): the workshop's icon, else its logo squeezed, else Ramplight's mark.
 */
export function WorkshopLogo({
  size = 'header',
  withTagline = false,
  compact = false,
  onPaper = false,
  className,
}: {
  size?: keyof typeof SIZES;
  withTagline?: boolean;
  /** Printed (white paper): always the light-mode logo. */
  onPaper?: boolean;
  compact?: boolean;
  className?: string;
}) {
  const { branding, displayName } = useBranding();
  const dark = useResolvedTheme() === 'dark' && !onPaper;
  const alt = displayName ?? 'Workshop logo';

  if (compact && branding.iconUrl) {
    return (
      <img
        src={branding.iconUrl}
        alt={alt}
        data-testid="workshop-icon"
        className={cn('block size-9 rounded-md object-contain', className)}
      />
    );
  }
  const logo = (dark && branding.logoDarkUrl) || branding.logoUrl;
  if (!logo) {
    return <Logo withTagline={withTagline} compact={compact} className={className} />;
  }
  return (
    <img
      src={logo}
      alt={alt}
      data-testid="workshop-logo"
      className={cn('block w-auto object-contain', compact ? 'h-8 max-w-10' : SIZES[size], className)}
    />
  );
}
