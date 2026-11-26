import type { Role } from '@/api/types';

export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  SERVICE_ADVISOR: 'Service advisor',
  MECHANIC: 'Mechanic',
  MANAGER: 'Manager',
  ADMIN: 'Admin',
  PLATFORM_ADMIN: 'Ramplight team',
};

export const ALL_ROLES: Role[] = ['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN'];

/**
 * The roles a user acts with: a MANAGER (a location's manager) may do everything a service
 * advisor may, as in the API.
 */
export function effectiveRoles(roles: Role[]): Role[] {
  return roles.includes('MANAGER') && !roles.includes('SERVICE_ADVISOR')
    ? [...roles, 'SERVICE_ADVISOR']
    : roles;
}

/** Staff roles in the order they are listed (one person may have several). */
export const STAFF_ROLES: Role[] = ['ADMIN', 'MANAGER', 'SERVICE_ADVISOR', 'MECHANIC'];

/** What each staff role does, in one line (shown where roles are chosen). */
export const ROLE_HINTS: Partial<Record<Role, string>> = {
  ADMIN: 'Runs the workshop: settings, users and locations, at every location.',
  MANAGER: "Runs a location: the desk's work plus its numbers and canned jobs.",
  SERVICE_ADVISOR: 'The front desk: bookings, check-in, estimates and invoices.',
  MECHANIC: 'Works on cars: gets My jobs and records the work done.',
};

const ROLE_SHORT: Record<Role, string> = {
  CUSTOMER: 'Customer',
  SERVICE_ADVISOR: 'Advisor',
  MECHANIC: 'Mechanic',
  MANAGER: 'Manager',
  ADMIN: 'Admin',
  PLATFORM_ADMIN: 'Ramplight team',
};

/** "Admin · Advisor · Mechanic": a person's roles, short, in a fixed order. */
export function rolesSummary(roles: Role[]): string {
  const order: Role[] = ['ADMIN', 'MANAGER', 'SERVICE_ADVISOR', 'MECHANIC', 'CUSTOMER', 'PLATFORM_ADMIN'];
  return order
    .filter((r) => roles.includes(r))
    .map((r) => ROLE_SHORT[r])
    .join(' · ');
}

/** Roles that work at particular locations unless the person is also an ADMIN (who works everywhere). */
export const LOCATION_SCOPED_ROLES: Role[] = ['MANAGER', 'SERVICE_ADVISOR', 'MECHANIC'];

export const worksAtChosenLocations = (roles: Role[]) =>
  !roles.includes('ADMIN') && roles.some((r) => LOCATION_SCOPED_ROLES.includes(r));
