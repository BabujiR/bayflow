import { Logo } from '@/components/layout/Logo';
import { cn } from '@/lib/utils';

import { useBranding } from './use-branding';

/** The workshop's own logo when it has one, otherwise Bayflow's. */
export function WorkshopLogo({
  size = 'header',
  withTagline = false,
  className,
}: {
  size?: 'header' | 'large';
  withTagline?: boolean;
  className?: string;
}) {
  const { branding, displayName } = useBranding();
  if (!branding.logoUrl) {
    return <Logo withTagline={withTagline} className={className} />;
  }
  return (
    <img
      src={branding.logoUrl}
      alt={displayName ?? 'Workshop logo'}
      data-testid="workshop-logo"
      className={cn(
        'block w-auto object-contain',
        size === 'header' ? 'h-9 max-w-40' : 'h-16 max-w-64',
        className,
      )}
    />
  );
}
