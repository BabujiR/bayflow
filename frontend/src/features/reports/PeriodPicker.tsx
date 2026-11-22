import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';

import {
  customPeriod,
  customPeriodProblem,
  periodFor,
  PERIOD_LABELS,
  type Period,
  type PeriodPreset,
} from './period';

const PRESETS = Object.keys(PERIOD_LABELS) as PeriodPreset[];

/** The period of a report: a preset, or two dates in the workshop's calendar. */
export function PeriodPicker({ period, onChange }: { period: Period; onChange: (next: Period) => void }) {
  const [custom, setCustom] = useState(period.preset === 'custom');
  const [first, setFirst] = useState(period.firstDay);
  const [last, setLast] = useState(period.lastDay);
  const problem = customPeriodProblem(first, last);

  return (
    <div className="flex flex-wrap items-end gap-2">
      <label className="grid gap-1 text-sm">
        <span className="text-muted-foreground">Period</span>
        <Select
          aria-label="Period"
          className="h-9 w-40"
          value={custom ? 'custom' : period.preset}
          onChange={(e) => {
            const preset = e.target.value as PeriodPreset;
            if (preset === 'custom') {
              setCustom(true);
              setFirst(period.firstDay);
              setLast(period.lastDay);
              return;
            }
            setCustom(false);
            onChange(periodFor(preset));
          }}
        >
          {PRESETS.map((preset) => (
            <option key={preset} value={preset}>
              {PERIOD_LABELS[preset]}
            </option>
          ))}
        </Select>
      </label>
      {custom && (
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (!problem) {
              onChange(customPeriod(first, last));
            }
          }}
        >
          <label className="grid gap-1 text-sm">
            <span className="text-muted-foreground">From</span>
            <Input
              type="date"
              className="h-9 w-40"
              value={first}
              onChange={(e) => setFirst(e.target.value)}
            />
          </label>
          <label className="grid gap-1 text-sm">
            <span className="text-muted-foreground">To</span>
            <Input type="date" className="h-9 w-40" value={last} onChange={(e) => setLast(e.target.value)} />
          </label>
          <Button type="submit" size="sm" className="h-9" disabled={!!problem} title={problem ?? undefined}>
            Apply
          </Button>
          {problem && first && last && (
            <p role="alert" className="w-full text-sm text-destructive">
              {problem}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
