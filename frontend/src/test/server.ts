import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

import type {
  AppNotification,
  Appointment,
  BookingHours,
  Branding,
  PublicBookingOptions,
  PublicBranding,
  PublicWorkshop,
  Customer,
  JobCard,
  PageResponse,
  PeriodMetrics,
  ReportSummary,
  Role,
  TokenResponse,
  User,
  Location,
  Vehicle,
  Workshop,
} from '@/api/types';

export function pageOf<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

/**
 * Background requests most screens make. Tests override these with server.use(...);
 * resetHandlers() after each test restores them.
 */
const defaults = [
  http.get('*/api/v1/auth/tenant', () => HttpResponse.json(workshop())),
  http.get('*/api/v1/auth/locations', () => HttpResponse.json([location()])),
  http.get('*/api/v1/tenants/current', () => HttpResponse.json(publicWorkshop())),
  http.get('*/api/v1/auth/tenant/branding', () => HttpResponse.json(branding())),
  http.get('*/api/v1/auth/tenant/branding/announcements', () => HttpResponse.json([])),
  http.get('*/api/v1/notifications/unread-count', () => HttpResponse.json({ count: 0 })),
  http.get('*/api/v1/notifications', () => HttpResponse.json(pageOf([]))),
  http.get('*/api/v1/customers/me', () => HttpResponse.json(customer())),
  http.get('*/api/v1/vehicles', () => HttpResponse.json(pageOf([]))),
  http.get('*/api/v1/appointments', () => HttpResponse.json(pageOf([]))),
  http.get('*/api/v1/job-cards', () => HttpResponse.json(pageOf([]))),
  // A completed job card asks for its invoice: none by default.
  http.get('*/api/v1/job-cards/:id/invoice', () =>
    HttpResponse.json({ status: 404, title: 'Not Found', detail: 'Invoice was not found.' }, { status: 404 }),
  ),
  // Maintenance reminders: nothing due, no intervals.
  http.get('*/api/v1/service-due/vehicles/:vehicleId', () => HttpResponse.json([])),
  http.get('*/api/v1/service-due', () => HttpResponse.json([])),
  http.get('*/api/v1/service-intervals', () => HttpResponse.json([])),
  http.get('*/api/v1/service-reminders/me', () => HttpResponse.json({ enabled: true })),
  http.get('*/api/v1/service-reminders/settings', () =>
    HttpResponse.json({ enabled: true, remindDaysBefore: 14, milesPerDay: 30, version: 0 }),
  ),
  // Canned jobs: none yet (advisors see the picker only when there are some).
  http.get('*/api/v1/canned-jobs', () => HttpResponse.json([])),
  http.get('*/api/v1/booking-services', () => HttpResponse.json([])),
  http.get('*/api/v1/booking-services/hours/:locationId', ({ params }) =>
    HttpResponse.json(bookingHours({ locationId: String(params.locationId) })),
  ),
  http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([]))),
  // QuickBooks: not set up on the server.
  http.get('*/api/v1/quickbooks', () =>
    HttpResponse.json({
      available: false,
      connected: false,
      ready: false,
      environment: null,
      companyName: null,
      country: null,
      connectedAt: null,
      connectedBy: null,
      mapping: null,
    }),
  ),
  // The owner's dashboard (home page of admins and managers): an empty period.
  http.get('*/api/v1/reports/summary', () => HttpResponse.json(reportSummary())),
  http.get('*/api/v1/reports/daily', () => HttpResponse.json({ from: '', to: '', zone: 'UTC', days: [] })),
  http.get('*/api/v1/reports/technicians', () => HttpResponse.json([])),
  http.get('*/api/v1/reports/locations', () => HttpResponse.json([])),
  http.get('*/api/v1/reports/advisors', () => HttpResponse.json([])),
  http.get('*/api/v1/reports/declined', () =>
    HttpResponse.json({ from: '', to: '', rows: [], truncated: false }),
  ),
  http.get('*/api/v1/reports/payments', () =>
    HttpResponse.json({ from: '', to: '', rows: [], truncated: false, byMethod: [], total: 0 }),
  ),
  http.get('*/api/v1/reports/unpaid', () =>
    HttpResponse.json({
      asOf: '',
      rows: [],
      truncated: false,
      buckets: [],
      totalBalance: 0,
      overdueBalance: 0,
    }),
  ),
  http.get('*/api/v1/reports/tax', () => HttpResponse.json({ from: '', to: '', rows: [], totalTax: 0 })),
  http.get('*/api/v1/reports/parts', () =>
    HttpResponse.json({ from: '', to: '', rows: [], truncated: false }),
  ),
];

/** MSW server shared by all tests. */
export const server = setupServer(...defaults);

/** A location's booking hours as the server sends them before they are first saved. */
export function bookingHours(overrides: Partial<BookingHours> = {}): BookingHours {
  const weekday = { opens: '08:00', closes: '17:00' };
  const closed = { opens: null, closes: null };
  return {
    locationId: 'loc-1',
    days: [weekday, weekday, weekday, weekday, weekday, closed, closed],
    slotMinutes: 30,
    carsPerSlot: 2,
    minNoticeMinutes: 120,
    maxDaysAhead: 30,
    version: 0,
    ...overrides,
  };
}

/** What the public booking page offers: one location, two services. */
export function bookingOptions(overrides: Partial<PublicBookingOptions> = {}): PublicBookingOptions {
  return {
    shopName: 'Demo Auto',
    currency: 'USD',
    locations: [{ code: 'main', name: 'Main', address: '1 Main St', timeZone: 'America/New_York' }],
    services: [
      {
        code: 'oil-change',
        name: 'Oil change',
        description: 'Oil and filter',
        priceKind: 'FROM',
        price: 54,
        durationMinutes: 45,
        locations: null,
      },
      {
        code: 'brakes',
        name: 'Brake check',
        description: null,
        priceKind: 'FREE',
        price: null,
        durationMinutes: 30,
        locations: null,
      },
    ],
    ...overrides,
  };
}

export function user(role: Role = 'CUSTOMER', username = 'customer1'): User {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    username,
    email: `${username}@bayflow.local`,
    roles: [role],
    enabled: true,
    createdAt: '2026-11-01T10:00:00Z',
  };
}

export function token(u: User = user(), accessToken = 'access-1'): TokenResponse {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, expiresAt: '2026-11-10T20:00:00Z', user: u };
}

export function problem(
  status: number,
  type: string,
  detail: string,
  errors?: { field: string; message: string }[],
) {
  return HttpResponse.json(
    { type: `urn:bayflow:problem:${type}`, title: 'Error', status, detail, errors },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** The session-restore call made on every app start: no cookie → 401. */
export const noSession = http.post('*/api/v1/auth/refresh', () =>
  problem(401, 'invalid-refresh-token', 'Please sign in again.'),
);

/** A valid refresh cookie for the given user. */
export function sessionFor(u: User) {
  return http.post('*/api/v1/auth/refresh', () => HttpResponse.json(token(u)));
}

export function customer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: 'c0000000-0000-0000-0000-000000000001',
    userId: '00000000-0000-0000-0000-000000000001',
    name: 'Jane Doe',
    email: 'jane@example.com',
    phone: '+1 555 0100',
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function vehicle(overrides: Partial<Vehicle> = {}): Vehicle {
  return {
    id: 'v0000000-0000-0000-0000-000000000001',
    customerId: 'c0000000-0000-0000-0000-000000000001',
    vin: '1HGCM82633A004352',
    registration: 'BFL 1234',
    make: 'Toyota',
    model: 'Corolla',
    year: 2021,
    mileage: 12000,
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: 'a0000000-0000-0000-0000-000000000001',
    locationId: '00000000-0000-0000-0000-000000000001',
    customerId: 'c0000000-0000-0000-0000-000000000001',
    customerName: 'Jane Doe',
    vehicle: {
      id: 'v1',
      vin: '1HGCM82633A004352',
      registration: 'BFL 1234',
      make: 'Toyota',
      model: 'Corolla',
      year: 2021,
    },
    scheduledAt: '2030-01-15T15:00:00Z',
    endsAt: '2030-01-15T16:00:00Z',
    durationMinutes: 60,
    serviceType: 'BRAKES',
    status: 'BOOKED',
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function jobCard(overrides: Partial<JobCard> = {}): JobCard {
  return {
    id: 'j0000000-0000-0000-0000-000000000001',
    appointmentId: 'a0000000-0000-0000-0000-000000000001',
    locationId: '00000000-0000-0000-0000-000000000001',
    vehicleId: 'v0000000-0000-0000-0000-000000000001',
    vin: '1HGCM82633A004352',
    registration: 'BFL 1234',
    customerUserId: '00000000-0000-0000-0000-000000000001',
    customerName: 'Jane Doe',
    mileage: 12480,
    status: 'OPEN',
    findings: [],
    recommendations: [],
    workLogs: [],
    parts: [],
    labour: [],
    labourRate: 95,
    currency: 'USD',
    estimatedTotal: 0,
    approvedEstimate: 0,
    createdAt: '2026-11-10T14:00:00Z',
    updatedAt: '2026-11-10T14:00:00Z',
    ...overrides,
  };
}

/** The signed-in user's workshop (default workshop, New York, USD). */
/** The workshop at this address, as its sign-in page sees it (Bayflow's look by default). */
export function publicWorkshop(
  overrides: Partial<Omit<PublicWorkshop, 'branding'>> & { branding?: Partial<PublicBranding> } = {},
): PublicWorkshop {
  const { branding: brandingOverrides, ...rest } = overrides;
  return {
    slug: 'demo',
    name: 'Bayflow Workshop',
    displayName: 'Bayflow Downtown',
    active: true,
    ...rest,
    branding: {
      logoUrl: null,
      coverUrl: null,
      primaryColor: null,
      showPoweredBy: true,
      announcements: [],
      ...brandingOverrides,
    },
  };
}

/** The admin's view of the branding (none set by default). */
export function branding(overrides: Partial<Branding> = {}): Branding {
  return {
    primaryColor: null,
    showPoweredBy: true,
    logoUrl: null,
    coverUrl: null,
    version: 0,
    ...overrides,
  };
}

export function workshop(overrides: Partial<Workshop> = {}): Workshop {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    slug: 'default',
    name: 'Bayflow Workshop',
    displayName: 'Bayflow Downtown',
    phone: '+1 555 0100',
    email: 'desk@bayflow.local',
    address: '1 Main St',
    labourRate: 95,
    currency: 'USD',
    timeZone: 'America/New_York',
    version: 0,
    ...overrides,
  };
}

/** A location of the organization; by default its first one, "Main" (id = the tenant id). */
export function location(overrides: Partial<Location> = {}): Location {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    code: 'main',
    name: 'Main',
    status: 'ACTIVE',
    timeZone: 'America/New_York',
    labourRate: 95,
    currency: 'USD',
    ownTimeZone: null,
    ownLabourRate: null,
    version: 0,
    ...overrides,
  };
}

export function notification(overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id: '0e0e0e0e-0000-4000-8000-000000000001',
    type: 'ESTIMATE_READY',
    title: 'Your estimate is ready',
    body: 'Review and approve the recommended work for BFL 1234.',
    link: '/job-cards/0f0f0f0f-0000-4000-8000-000000000001',
    locationId: '00000000-0000-0000-0000-000000000001',
    createdAt: '2026-11-19T15:00:00Z',
    ...overrides,
  };
}

/** A period's figures; all zero unless overridden. */
export function periodMetrics(overrides: Partial<PeriodMetrics> = {}): PeriodMetrics {
  return {
    carCount: 0,
    sales: 0,
    partsSales: 0,
    labourSales: 0,
    averageRepairOrder: 0,
    labourHours: 0,
    effectiveLabourRate: 0,
    itemsApproved: 0,
    itemsDeclined: 0,
    approvedValue: 0,
    declinedValue: 0,
    hoursApproved: 0,
    hoursDeclined: 0,
    closeRatio: null,
    feesSales: 0,
    discounts: 0,
    collected: 0,
    ...overrides,
  };
}

export function reportSummary(
  current: Partial<PeriodMetrics> = {},
  previous: Partial<PeriodMetrics> = {},
  open: Partial<ReportSummary['open']> = {},
): ReportSummary {
  return {
    from: '2026-11-01T04:00:00Z',
    to: '2026-11-22T05:00:00Z',
    current: periodMetrics(current),
    previousFrom: '2026-10-11T04:00:00Z',
    previousTo: '2026-11-01T04:00:00Z',
    previous: periodMetrics(previous),
    open: {
      openCards: 0,
      awaitingApproval: 0,
      inWork: 0,
      pendingValue: 0,
      unpaidInvoices: 0,
      unpaidBalance: 0,
      overdueBalance: 0,
      ...open,
    },
  };
}
