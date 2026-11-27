import type { Role } from '@/api/types';
import { ROLE_HINTS, ROLE_LABELS, STAFF_ROLES } from '@/auth/roles';
import { MultiSelect } from '@/components/ui/multi-select';

/**
 * The staff roles as a multi-select dropdown: one person may do several jobs (e.g. Admin +
 * Service advisor + Mechanic for an owner who also runs the desk and works on cars). `locked`:
 * ticked and can't be unticked (your own admin role).
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
    <MultiSelect
      label="Roles"
      placeholder="Choose roles…"
      hint={
        locked.includes('ADMIN')
          ? "Tick everything this person does. You can't remove your own admin role."
          : 'Tick everything this person does.'
      }
      options={STAFF_ROLES.map((role) => ({
        value: role,
        label: ROLE_LABELS[role],
        hint: ROLE_HINTS[role],
        locked: locked.includes(role),
      }))}
      value={value}
      onChange={(next) => onChange(next as Role[])}
      error={error}
    />
  );
}
