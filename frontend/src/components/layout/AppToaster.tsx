import { Toaster } from 'sonner';

import { useTheme } from '@/lib/theme';

export function AppToaster() {
  const { resolvedTheme } = useTheme();
  return <Toaster theme={resolvedTheme} position="top-right" richColors closeButton />;
}
