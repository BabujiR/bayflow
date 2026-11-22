import { Logo } from '@/components/layout/Logo';
import { cn } from '@/lib/utils';

import { useBranding } from './use-branding';

/** The workshop's own logo when it has one, otherwise Bayflow's. */
export function WorkshopLogo({
  size = 'header',
  withTagline = false,
  compact = false,
  className,
}: {
  size?: 'header' | 'large';
  withTagline?: boolean;
  /** Narrow (collapsed sidebar): the product's mark only, or the logo squeezed to an icon. */
  compact?: boolean;
  className?: string;
}) {
  const { branding, displayName } = useBranding();
  if (!branding.logoUrl) {
    return <Logo withTagline={withTagline} compact={compact} className={className} />;
  }
  return (
    <img
      src={branding.logoUrl}
      alt={displayName ?? 'Workshop logo'}
      data-testid="workshop-logo"
      className={cn(
        'block w-auto object-contain',
        compact ? 'h-8 max-w-10' : size === 'header' ? 'h-9 max-w-40' : 'h-16 max-w-64',
        className,
      )}
    />
  );
}
