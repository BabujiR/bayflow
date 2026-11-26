import type { Role } from '@/api/types';
import { ROLE_HINTS, ROLE_LABELS, STAFF_ROLES } from '@/auth/roles';
import { cn } from '@/lib/utils';

/**
 * The staff roles as checkboxes: one person may do several jobs (e.g. Admin + Service advisor
 * + Mechanic for an owner who also runs the desk and works on cars). `locked`: shown ticked and
 * can't be unticked (your own admin role).
 */
export function RoleChecklist({
  value,
  onChange,
  error,
  locked = [],
}: {
  value: Role[];
  onChange: (roles: Role[]) => void;
  error?: string;
  locked?: Role[];
}) {
  return (
    <fieldset className="grid gap-2" aria-describedby={error ? 'roles-error' : undefined}>
      <legend className="mb-1 text-sm font-medium">Roles</legend>
      <p className="-mt-1 text-sm text-muted-foreground">Tick everything this person does.</p>
      {STAFF_ROLES.map((role) => {
        const checked = value.includes(role);
        const isLocked = locked.includes(role);
        return (
          <label
            key={role}
            className={cn(
              'flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm transition-colors hover:border-primary/60',
              checked && 'border-primary bg-primary/5',
              isLocked && 'cursor-not-allowed opacity-80',
            )}
          >
            <input
              type="checkbox"
              className="mt-0.5"
              checked={checked}
              disabled={isLocked}
              onChange={(e) =>
                onChange(e.target.checked ? [...value, role] : value.filter((r) => r !== role))
              }
            />
            <span className="grid gap-0.5">
              <span className="font-medium">{ROLE_LABELS[role]}</span>
              <span className="text-muted-foreground">
                {ROLE_HINTS[role]}
                {isLocked && " You can't remove your own admin role."}
              </span>
            </span>
          </label>
        );
      })}
      {error && (
        <p id="roles-error" role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </fieldset>
  );
}
