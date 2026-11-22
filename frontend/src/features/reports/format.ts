/** Number formats for the reports. */

export function formatPercent(ratio: number | null | undefined, digits = 0): string {
  if (ratio === null || ratio === undefined) {
    return '—';
  }
  return new Intl.NumberFormat(undefined, {
    style: 'percent',
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(ratio);
}

export function formatHours(hours: number | null | undefined): string {
  if (hours === null || hours === undefined) {
    return '—';
  }
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(hours)} h`;
}

export const formatCount = (n: number) => new Intl.NumberFormat().format(n);

/**
 * Change from `previous` to `current` as a ratio (0.12 = 12 % up), or null when there is
 * nothing to compare with.
 */
export function change(current: number | null, previous: number | null): number | null {
  if (current === null || previous === null || previous === 0) {
    return null;
  }
  return (current - previous) / Math.abs(previous);
}

/** "▲ 12%", "▼ 3%", "No change", or "New" when last period was zero. */
export function describeChange(current: number | null, previous: number | null): string {
  if (current === null || previous === null) {
    return '';
  }
  if (previous === 0) {
    return current === 0 ? 'No change' : 'New';
  }
  const ratio = change(current, previous) ?? 0;
  if (Math.abs(ratio) < 0.005) {
    return 'No change';
  }
  return `${ratio > 0 ? '▲' : '▼'} ${formatPercent(Math.abs(ratio))}`;
}

/** Change in percentage points, for ratios such as the close ratio: "▲ 4 pts". */
export function describePointChange(current: number | null, previous: number | null): string {
  if (current === null || previous === null) {
    return '';
  }
  const points = Math.round((current - previous) * 100);
  if (points === 0) {
    return 'No change';
  }
  return `${points > 0 ? '▲' : '▼'} ${Math.abs(points)} pts`;
}
