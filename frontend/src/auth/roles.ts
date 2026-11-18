import type { Role } from '@/api/types';

export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  SERVICE_ADVISOR: 'Service advisor',
  MECHANIC: 'Mechanic',
  MANAGER: 'Manager',
  ADMIN: 'Admin',
};

export const ALL_ROLES: Role[] = ['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'MANAGER', 'ADMIN'];

/**
 * The roles a user acts with: a MANAGER (a location's manager) may do everything a service
 * advisor may, as in the API.
 */
export function effectiveRoles(roles: Role[]): Role[] {
  return roles.includes('MANAGER') && !roles.includes('SERVICE_ADVISOR') ? [...roles, 'SERVICE_ADVISOR'] : roles;
}
