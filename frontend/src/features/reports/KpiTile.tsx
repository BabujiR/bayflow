import type { ReactNode } from 'react';

import { Card } from '@/components/ui/card';

/**
 * One headline number. The change against the previous period is spelled out (arrow and
 * words), never shown by colour alone.
 */
export function KpiTile({
  label,
  value,
  changeText,
  hint,
}: {
  label: string;
  value: ReactNode;
  changeText?: string;
  hint?: ReactNode;
}) {
  return (
    <Card className="grid gap-1 p-4" data-testid="kpi">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-2xl font-semibold tabular-nums tracking-tight">{value}</p>
      {(changeText || hint) && (
        <p className="text-xs text-muted-foreground">
          {changeText && <span className="font-medium text-foreground">{changeText}</span>}
          {changeText && hint && ' '}
          {hint}
        </p>
      )}
    </Card>
  );
}
