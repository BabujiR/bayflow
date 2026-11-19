import { publicSiteUrl } from '@/lib/site';

import { useBranding } from './use-branding';

/** "Powered by Bayflow", unless the workshop has turned it off. */
export function PoweredBy() {
  const { showPoweredBy } = useBranding().branding;
  if (!showPoweredBy) {
    return null;
  }
  return (
    <footer className="px-4 py-6 text-center text-xs text-muted-foreground" data-testid="powered-by">
      Powered by{' '}
      <a href={publicSiteUrl()} className="font-medium underline-offset-4 hover:underline">
        Bayflow
      </a>
    </footer>
  );
}
