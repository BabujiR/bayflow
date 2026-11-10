import type { AppointmentStatus, JobCardStatus, ServiceType, Severity } from '@/api/types';

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });
const timeOnly = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' });

export const formatDateTime = (iso?: string | null) => (iso ? dateTime.format(new Date(iso)) : '—');
export const formatDate = (iso?: string | null) => (iso ? dateOnly.format(new Date(iso)) : '—');
export const formatTime = (iso?: string | null) => (iso ? timeOnly.format(new Date(iso)) : '—');

export function formatMoney(amount: number | null | undefined, currency = 'USD'): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
}

export const formatMileage = (miles?: number | null) =>
  miles === null || miles === undefined ? '—' : `${new Intl.NumberFormat().format(miles)} mi`;

/** Today's date as YYYY-MM-DD in the browser's time zone (the workshop's, for staff). */
export function todayIso(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

/** A datetime-local input value (local time) as an ISO instant. */
export function localInputToIso(value: string): string {
  return new Date(value).toISOString();
}

/** An ISO instant as a datetime-local input value (local time). */
export function isoToLocalInput(iso: string): string {
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = {
  ROUTINE_SERVICE: 'Routine service',
  OIL_CHANGE: 'Oil change',
  INSPECTION: 'Inspection',
  BRAKES: 'Brakes',
  TYRES: 'Tyres',
  DIAGNOSTIC: 'Diagnostic',
  REPAIR: 'Repair',
  OTHER: 'Other',
};

export const APPOINTMENT_STATUS_LABELS: Record<AppointmentStatus, string> = {
  BOOKED: 'Booked',
  CONFIRMED: 'Confirmed',
  ARRIVED: 'Arrived',
  CHECKED_IN: 'Checked in',
  CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show',
};

export const JOB_CARD_STATUS_LABELS: Record<JobCardStatus, string> = {
  OPEN: 'Open',
  INSPECTED: 'Inspected',
  AWAITING_APPROVAL: 'Awaiting approval',
  APPROVED: 'Approved',
  PARTIALLY_APPROVED: 'Partially approved',
  DECLINED: 'Declined',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
};

export const SEVERITY_LABELS: Record<Severity, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};
