import type { AppointmentStatus, JobCardStatus, ServiceType, Severity } from '@/api/types';

/**
 * Dates and times are shown in the workshop's time zone (its own setting), so a customer
 * abroad and the front desk see the same booking time. Until the workshop is known (sign-in,
 * tests) the browser's zone is used.
 */
let workshopZone: string | undefined;
const formatters = new Map<string, Intl.DateTimeFormat>();

/** Called when the signed-in user's workshop is loaded; undefined goes back to the browser's zone. */
export function setWorkshopZone(zone: string | undefined): void {
  if (zone !== workshopZone) {
    workshopZone = zone;
    formatters.clear();
  }
}

export const getWorkshopZone = () => workshopZone;

function formatter(key: string, options: Intl.DateTimeFormatOptions, locale?: string): Intl.DateTimeFormat {
  let f = formatters.get(key);
  if (!f) {
    f = new Intl.DateTimeFormat(locale, { ...options, timeZone: workshopZone });
    formatters.set(key, f);
  }
  return f;
}

export const formatDateTime = (iso?: string | null) =>
  iso ? formatter('dateTime', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso)) : '—';
export const formatDate = (iso?: string | null) =>
  iso ? formatter('date', { dateStyle: 'medium' }).format(new Date(iso)) : '—';
export const formatTime = (iso?: string | null) =>
  iso ? formatter('time', { timeStyle: 'short' }).format(new Date(iso)) : '—';

export function formatMoney(amount: number | null | undefined, currency = 'USD'): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
}

/** The short symbol for a currency code, e.g. "$" for USD, "€" for EUR. */
export function currencySymbol(currency = 'USD'): string {
  const part = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    currencyDisplay: 'narrowSymbol',
  })
    .formatToParts(0)
    .find((p) => p.type === 'currency');
  return part?.value ?? currency;
}

export const formatMileage = (miles?: number | null) =>
  miles === null || miles === undefined ? '—' : `${new Intl.NumberFormat().format(miles)} mi`;

/** Wall-clock parts of an instant in the workshop zone. */
function wallClock(date: Date) {
  const parts = formatter(
    'parts',
    {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    },
    'en-US',
  ).formatToParts(date);
  const get = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((p) => p.type === type)?.value);
  return {
    year: get('year'),
    month: get('month'),
    day: get('day'),
    hour: get('hour') % 24,
    minute: get('minute'),
  };
}

const pad = (n: number, width = 2) => String(n).padStart(width, '0');

/** Today's date as YYYY-MM-DD in the workshop's time zone. */
export function todayIso(now: Date = new Date()): string {
  const { year, month, day } = wallClock(now);
  return `${pad(year, 4)}-${pad(month)}-${pad(day)}`;
}

/** An ISO instant as a datetime-local input value, in the workshop's time zone. */
export function isoToLocalInput(iso: string): string {
  const { year, month, day, hour, minute } = wallClock(new Date(iso));
  return `${pad(year, 4)}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}`;
}

/**
 * A datetime-local input value (workshop time) as epoch milliseconds; NaN when the value is
 * empty or not a date, so form checks can reject it without throwing.
 */
export function localInputToMillis(value: string): number {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value ?? '');
  if (!match) {
    return Number.NaN;
  }
  if (!workshopZone) {
    return new Date(value).getTime();
  }
  const [y, mo, d, h, mi] = match.slice(1).map(Number);
  const asUtc = Date.UTC(y, mo - 1, d, h, mi);
  // The zone's offset at that moment; checked twice so a DST change in between is handled.
  const offsetAt = (instant: number) => {
    const w = wallClock(new Date(instant));
    return Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute) - instant;
  };
  const first = asUtc - offsetAt(asUtc);
  return asUtc - offsetAt(first);
}

/** A datetime-local input value (workshop time) as an ISO instant. */
export function localInputToIso(value: string): string {
  return new Date(localInputToMillis(value)).toISOString();
}

/** Short name of the workshop's zone right now, e.g. "EST" or "GMT+13". */
export function zoneLabel(at: Date = new Date()): string {
  const part = formatter('zone', { timeZoneName: 'short' })
    .formatToParts(at)
    .find((p) => p.type === 'timeZoneName');
  return part?.value ?? '';
}

/** Every IANA time zone the browser knows, for the settings form. */
export function timeZones(): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (key: 'timeZone') => string[] };
  const zones = intl.supportedValuesOf?.('timeZone') ?? [];
  // Some engines leave out UTC from the list.
  return zones.includes('UTC') ? zones : ['UTC', ...zones];
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
