import type { ComponentType, SVGProps } from 'react';

import type { Role } from '@/api/types';
import {
  BoardIcon,
  CalendarIcon,
  CarIcon,
  ChartIcon,
  DashboardIcon,
  IdCardIcon,
  JobCardIcon,
  MapPinIcon,
  SettingsIcon,
  UsersIcon,
  WrenchIcon,
} from '@/components/ui/icons';

/** Sections of the staff sidebar, in order. */
export type NavGroup = 'work' | 'insights' | 'admin';

export const NAV_GROUP_LABELS: Record<NavGroup, string> = {
  work: 'Workshop',
  insights: 'Insights',
  admin: 'Admin',
};

const GROUP_ORDER: NavGroup[] = ['work', 'insights', 'admin'];

export interface NavItem {
  to: string;
  label: string;
  description: string;
  roles: Role[];
  group: NavGroup;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
}

/** Main navigation. Each user sees only the items for their role. */
export const NAV_ITEMS: NavItem[] = [
  {
    to: '/dashboard',
    label: 'Dashboard',
    description: 'Sales, car count, average repair order and close ratio at a glance.',
    roles: ['ADMIN', 'MANAGER'],
    group: 'work',
    icon: DashboardIcon,
  },
  {
    to: '/board',
    label: "Today's board",
    description: "Today's appointments by status: confirm, mark arrived and check in.",
    roles: ['SERVICE_ADVISOR'],
    group: 'work',
    icon: BoardIcon,
  },
  {
    to: '/my-jobs',
    label: 'My jobs',
    description: 'Approved and in-progress job cards waiting for work.',
    roles: ['MECHANIC'],
    group: 'work',
    icon: WrenchIcon,
  },
  {
    to: '/appointments',
    label: 'Appointments',
    description: 'Book, reschedule or cancel service appointments.',
    roles: ['CUSTOMER', 'SERVICE_ADVISOR', 'ADMIN'],
    group: 'work',
    icon: CalendarIcon,
  },
  {
    to: '/vehicles',
    label: 'My vehicles',
    description: 'Your vehicles and their service history.',
    roles: ['CUSTOMER'],
    group: 'work',
    icon: CarIcon,
  },
  {
    to: '/job-cards',
    label: 'Job cards',
    description: 'Inspection findings, recommended work and approvals.',
    roles: ['CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN'],
    group: 'work',
    icon: JobCardIcon,
  },
  {
    to: '/customers',
    label: 'Customers',
    description: 'Find customers, their vehicles and service history.',
    roles: ['SERVICE_ADVISOR', 'ADMIN'],
    group: 'work',
    icon: UsersIcon,
  },
  {
    to: '/reports',
    label: 'Reports',
    description: 'Sales, advisors, technicians, declined work and parts, with CSV export.',
    roles: ['ADMIN', 'MANAGER'],
    group: 'insights',
    icon: ChartIcon,
  },
  {
    to: '/users',
    label: 'User accounts',
    description: 'Create staff accounts and enable or disable users.',
    roles: ['ADMIN'],
    group: 'admin',
    icon: IdCardIcon,
  },
  {
    to: '/locations',
    label: 'Locations',
    description: 'Your shops, with their own time zone and labour rate.',
    roles: ['ADMIN'],
    group: 'admin',
    icon: MapPinIcon,
  },
  {
    to: '/settings',
    label: 'Workshop settings',
    description: 'Name, contact details, labour rate, currency and time zone.',
    roles: ['ADMIN'],
    group: 'admin',
    icon: SettingsIcon,
  },
];

export function navItemsFor(roles: Role[]): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.some((role) => roles.includes(role)));
}

/** The user's items in sidebar sections; empty sections are left out. */
export function navGroupsFor(roles: Role[]): { group: NavGroup; label: string; items: NavItem[] }[] {
  const items = navItemsFor(roles);
  return GROUP_ORDER.map((group) => ({
    group,
    label: NAV_GROUP_LABELS[group],
    items: items.filter((item) => item.group === group),
  })).filter((section) => section.items.length > 0);
}
