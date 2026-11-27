import type { Location } from '@/api/types';
import { MultiSelect } from '@/components/ui/multi-select';

/** The locations someone works at, as a multi-select dropdown. */
export function LocationChecklist({
  locations,
  value,
  onChange,
  error,
}: {
  locations: Location[];
  value: string[];
  onChange: (ids: string[]) => void;
  error?: string;
}) {
  return (
    <MultiSelect
      label="Works at"
      placeholder="Choose locations…"
      options={locations.map((l) => ({ value: l.id, label: l.name, hint: l.timeZone.replaceAll('_', ' ') }))}
      value={value}
      onChange={onChange}
      error={error}
    />
  );
}
