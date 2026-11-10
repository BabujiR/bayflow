import type { Role } from '@/api/types';

export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Customer',
  SERVICE_ADVISOR: 'Service advisor',
  MECHANIC: 'Mechanic',
  ADMIN: 'Admin',
};

export const ALL_ROLES: Role[] = ['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN'];
