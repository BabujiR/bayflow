import type { Location } from '@/api/types';

/** Checkboxes for the locations someone works at. */
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
    <fieldset className="grid gap-2" aria-describedby={error ? 'location-checklist-error' : undefined}>
      <legend className="mb-1 text-sm font-medium">Works at</legend>
      {locations.map((l) => (
        <label key={l.id} className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4"
            checked={value.includes(l.id)}
            onChange={(e) => onChange(e.target.checked ? [...value, l.id] : value.filter((id) => id !== l.id))}
          />
          {l.name}
          <span className="text-xs text-muted-foreground">{l.timeZone.replaceAll('_', ' ')}</span>
        </label>
      ))}
      {error && (
        <p id="location-checklist-error" role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </fieldset>
  );
}
