import { Toaster } from 'sonner';

import { useTheme } from '@/lib/theme';

export function AppToaster() {
  const { resolvedTheme } = useTheme();
  // Top centre, just below the 64 px header: clear of the location switcher, bell and account menu.
  return <Toaster theme={resolvedTheme} position="top-center" offset={{ top: 72 }} richColors closeButton />;
}
