import type { Role } from '@/api/types';

export interface NavItem {
  to: string;
  label: string;
  description: string;
  roles: Role[];
}

/** Main navigation. Each user sees only the items for their role. */
export const NAV_ITEMS: NavItem[] = [
  {
    to: '/dashboard',
    label: 'Dashboard',
    description: 'Sales, car count, average repair order and close ratio at a glance.',
    roles: ['ADMIN', 'MANAGER'],
  },
  {
    to: '/board',
    label: "Today's board",
    description: "Today's appointments by status: confirm, mark arrived and check in.",
    roles: ['SERVICE_ADVISOR'],
  },
  {
    to: '/my-jobs',
    label: 'My jobs',
    description: 'Approved and in-progress job cards waiting for work.',
    roles: ['MECHANIC'],
  },
  {
    to: '/appointments',
    label: 'Appointments',
    description: 'Book, reschedule or cancel service appointments.',
    roles: ['CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN'],
  },
  {
    to: '/vehicles',
    label: 'My vehicles',
    description: 'Your vehicles and their service history.',
    roles: ['CUSTOMER'],
  },
  {
    to: '/job-cards',
    label: 'Job cards',
    description: 'Inspection findings, recommended work and approvals.',
    roles: ['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN'],
  },
  {
    to: '/customers',
    label: 'Customers',
    description: 'Find customers, their vehicles and service history.',
    roles: ['SERVICE_ADVISOR', 'ADMIN'],
  },
  {
    to: '/reports',
    label: 'Reports',
    description: 'Sales, advisors, technicians, declined work and parts, with CSV export.',
    roles: ['ADMIN', 'MANAGER'],
  },
  {
    to: '/users',
    label: 'User accounts',
    description: 'Create staff accounts and enable or disable users.',
    roles: ['ADMIN'],
  },
  {
    to: '/locations',
    label: 'Locations',
    description: 'Your shops, with their own time zone and labour rate.',
    roles: ['ADMIN'],
  },
  {
    to: '/settings',
    label: 'Workshop settings',
    description: 'Name, contact details, labour rate, currency and time zone.',
    roles: ['ADMIN'],
  },
];

export function navItemsFor(roles: Role[]): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.some((role) => roles.includes(role)));
}
