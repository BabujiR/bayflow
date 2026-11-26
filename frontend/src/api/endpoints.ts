import { http, request } from './http';
import type {
  AdvisorRow,
  Announcement,
  AnnouncementRequest,
  AppNotification,
  Appointment,
  Branding,
  BrandingImageKind,
  AppointmentStatus,
  BookAppointmentRequest,
  CreateCustomerRequest,
  CreateStaffRequest,
  CreateVehicleRequest,
  Customer,
  DailyReport,
  DeclinedRow,
  FindingRequest,
  JobCard,
  JobCardStatus,
  JobCardSummary,
  Invoice,
  InvoiceLineRequest,
  InvoiceSummary,
  InvoiceExport,
  CannedJob,
  CannedJobRequest,
  DemoRequest,
  BookingHours,
  BookingHoursRequest,
  BookingServiceItem,
  BookingServiceRequest,
  ConsoleDemoRequest,
  ConsoleInvitation,
  ConsoleWorkshop,
  DemoStatus,
  InvitationState,
  InviteDetails,
  IssuedInvitation,
  TestWorkshopRequest,
  DueService,
  DueVehicle,
  ReminderSettings,
  ServiceInterval,
  ServiceIntervalRequest,
  WorkRequest,
  QuickBooksChoice,
  QuickBooksMapping,
  QuickBooksStatus,
  LocationBilling,
  PaymentRequest,
  Location,
  LocationRequest,
  LocationSalesRow,
  LabourRequest,
  PageResponse,
  PartRequest,
  PartUsageRow,
  PaymentsReport,
  PublicWorkshop,
  ProfileRequest,
  RecommendationRequest,
  ReportRows,
  ReportSummary,
  RescheduleRequest,
  ServiceHistoryEntry,
  SignupRequest,
  SignupResponse,
  SlugAvailability,
  TaxReport,
  TechnicianRow,
  TemporaryPassword,
  UpdateBrandingRequest,
  UpdateVehicleRequest,
  UnpaidReport,
  UpdateWorkshopSettingsRequest,
  User,
  Vehicle,
  WorkLogRequest,
  Workshop,
  CustomerAccount,
  ManagedBooking,
  PublicAvailability,
  PublicBooked,
  PublicBookingOptions,
  PublicBookingRequest,
  Role,
} from './types';

export interface Paging {
  page?: number;
  size?: number;
}

// ---- appointment-service ---------------------------------------------------------------

export const customersApi = {
  me: () => http.get<Customer>('/api/v1/customers/me'),
  createMe: (body: ProfileRequest) => http.post<Customer>('/api/v1/customers/me', body),
  /** Adds an online booking (by its private-link token) and that booker's cars to my account. */
  claim: (token: string) => http.post<Customer>('/api/v1/customers/me/claim', { token }),
  updateMe: (body: ProfileRequest) => http.put<Customer>('/api/v1/customers/me', body),
  create: (body: CreateCustomerRequest) => http.post<Customer>('/api/v1/customers', body),
  list: (params: Paging & { q?: string }) =>
    http.get<PageResponse<Customer>>('/api/v1/customers', { ...params }),
  get: (id: string) => http.get<Customer>(`/api/v1/customers/${id}`),
};

export const vehiclesApi = {
  list: (params: Paging & { customerId?: string }) =>
    http.get<PageResponse<Vehicle>>('/api/v1/vehicles', { ...params }),
  get: (id: string) => http.get<Vehicle>(`/api/v1/vehicles/${id}`),
  create: (body: CreateVehicleRequest) => http.post<Vehicle>('/api/v1/vehicles', body),
  update: (id: string, body: UpdateVehicleRequest) => http.put<Vehicle>(`/api/v1/vehicles/${id}`, body),
};

export interface AppointmentFilters extends Paging {
  /** Workshop-local day, YYYY-MM-DD. */
  date?: string;
  status?: AppointmentStatus;
  vehicleId?: string;
  customerId?: string;
  /** One location; its day is the one `date` means (organizations with several locations). */
  locationId?: string;
  /** Default: earliest for one day, otherwise upcoming. */
  sort?: AppointmentSort;
}

/**
 * upcoming: not finished yet, soonest first, then the past, latest first; latest / earliest: by
 * appointment time; recent: most recently booked first.
 */
export type AppointmentSort = 'upcoming' | 'latest' | 'earliest' | 'recent';

export const appointmentsApi = {
  list: (filters: AppointmentFilters) =>
    http.get<PageResponse<Appointment>>('/api/v1/appointments', { ...filters }),
  get: (id: string) => http.get<Appointment>(`/api/v1/appointments/${id}`),
  book: (body: BookAppointmentRequest) => http.post<Appointment>('/api/v1/appointments', body),
  confirm: (id: string) => http.post<Appointment>(`/api/v1/appointments/${id}/confirm`),
  arrive: (id: string) => http.post<Appointment>(`/api/v1/appointments/${id}/arrive`),
  /** `vehicle`: the VIN and plate when an online booking didn't give them (the job card needs both). */
  checkIn: (id: string, mileage: number, vehicle?: { vin?: string; registration?: string }) =>
    http.post<Appointment>(`/api/v1/appointments/${id}/check-in`, { mileage, ...vehicle }),
  noShow: (id: string) => http.post<Appointment>(`/api/v1/appointments/${id}/no-show`),
  cancel: (id: string) => http.post<Appointment>(`/api/v1/appointments/${id}/cancel`),
  reschedule: (id: string, body: RescheduleRequest) =>
    http.patch<Appointment>(`/api/v1/appointments/${id}/reschedule`, body),
};

// ---- workshop-service ----------------------------------------------------------------------

export const jobCardsApi = {
  list: (params: Paging & { status?: JobCardStatus }) =>
    http.get<PageResponse<JobCardSummary>>('/api/v1/job-cards', { ...params }),
  get: (id: string) => http.get<JobCard>(`/api/v1/job-cards/${id}`),
  byAppointment: (appointmentId: string) =>
    http.get<JobCard>(`/api/v1/job-cards/by-appointment/${appointmentId}`),
  addFinding: (id: string, body: FindingRequest) =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/findings`, body),
  addRecommendation: (id: string, body: RecommendationRequest) =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/recommendations`, body),
  submit: (id: string) => http.post<JobCard>(`/api/v1/job-cards/${id}/submit-for-approval`),
  decide: (id: string, itemId: string, decision: 'approve' | 'decline') =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/recommendations/${itemId}/${decision}`),
  start: (id: string) => http.post<JobCard>(`/api/v1/job-cards/${id}/start`),
  logWork: (id: string, body: WorkLogRequest) =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/work-logs`, body),
  addPart: (id: string, body: PartRequest) => http.post<JobCard>(`/api/v1/job-cards/${id}/parts`, body),
  addLabour: (id: string, body: LabourRequest) => http.post<JobCard>(`/api/v1/job-cards/${id}/labour`, body),
  complete: (id: string) => http.post<JobCard>(`/api/v1/job-cards/${id}/complete`),
  /** A canned job's usual parts and labour for one approved item, all at once (mechanics). */
  addWork: (id: string, itemId: string, body: WorkRequest) =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/recommendations/${itemId}/work`, body),
};

export const remindersApi = {
  intervals: () => http.get<ServiceInterval[]>('/api/v1/service-intervals'),
  createInterval: (body: ServiceIntervalRequest) =>
    http.post<ServiceInterval>('/api/v1/service-intervals', body),
  updateInterval: (id: string, body: ServiceIntervalRequest) =>
    http.put<ServiceInterval>(`/api/v1/service-intervals/${id}`, body),
  deleteInterval: (id: string) => http.delete<void>(`/api/v1/service-intervals/${id}`),
  settings: () => http.get<ReminderSettings>('/api/v1/service-reminders/settings'),
  updateSettings: (body: Omit<ReminderSettings, 'version'> & { version: number }) =>
    http.put<ReminderSettings>('/api/v1/service-reminders/settings', body),
  /** Vehicles with a service due within `days` (or overdue), at the caller's locations. */
  dueSoon: (days: number, locationId?: string) =>
    http.get<DueVehicle[]>('/api/v1/service-due', { days, locationId }),
  /** Sends the customer a reminder now (409 when they have no account or turned reminders off). */
  remind: (vehicleId: string) => http.post<void>(`/api/v1/service-due/vehicles/${vehicleId}/remind`),
  /** The signed-in customer's own switch. */
  mine: () => http.get<{ enabled: boolean }>('/api/v1/service-reminders/me'),
  setMine: (enabled: boolean) => http.put<{ enabled: boolean }>('/api/v1/service-reminders/me', { enabled }),
  /** At check-in: the vehicle's services due within 30 days or 500 miles of `mileage`. */
  forVehicle: (vehicleId: string, mileage?: number) =>
    http.get<DueService[]>(`/api/v1/service-due/vehicles/${vehicleId}`, { mileage }),
};

/** Settings → Online booking: the services customers can book (ADMIN edits; staff read). */
export const bookingServicesApi = {
  list: () => http.get<BookingServiceItem[]>('/api/v1/booking-services'),
  create: (body: BookingServiceRequest) => http.post<BookingServiceItem>('/api/v1/booking-services', body),
  update: (id: string, body: BookingServiceRequest) =>
    http.put<BookingServiceItem>(`/api/v1/booking-services/${id}`, body),
  reorder: (ids: string[]) => http.put<BookingServiceItem[]>('/api/v1/booking-services/order', { ids }),
  /** A location's booking hours (defaults until saved). */
  hours: (locationId: string) => http.get<BookingHours>(`/api/v1/booking-services/hours/${locationId}`),
  setHours: (locationId: string, body: BookingHoursRequest) =>
    http.put<BookingHours>(`/api/v1/booking-services/hours/${locationId}`, body),
};

/** The public booking page (/book): no sign-in; the workshop is the one at this address. */
export const bookingApi = {
  options: () => http.get<PublicBookingOptions>('/api/v1/booking/options'),
  availability: (location: string, from: string, days = 7) =>
    http.get<PublicAvailability>('/api/v1/booking/availability', { location, from, days }),
  book: (body: PublicBookingRequest) => http.post<PublicBooked>('/api/v1/booking/appointments', body),
  /** The booking's private link (the token goes in the body, out of server logs). */
  manage: (token: string) => http.post<ManagedBooking>('/api/v1/booking/manage', { token }),
  reschedule: (token: string, startsAt: string) =>
    http.post<ManagedBooking>('/api/v1/booking/manage/reschedule', { token, startsAt }),
  cancel: (token: string, reason: string | null) =>
    http.post<ManagedBooking>('/api/v1/booking/manage/cancel', { token, reason }),
};

export const cannedJobsApi = {
  /** locationId: only those usable there; includeInactive: archived ones too (settings page). */
  list: (params: { locationId?: string; q?: string; includeInactive?: boolean } = {}) =>
    http.get<CannedJob[]>('/api/v1/canned-jobs', { ...params }),
  get: (id: string) => http.get<CannedJob>(`/api/v1/canned-jobs/${id}`),
  create: (body: CannedJobRequest) => http.post<CannedJob>('/api/v1/canned-jobs', body),
  update: (id: string, body: CannedJobRequest) => http.put<CannedJob>(`/api/v1/canned-jobs/${id}`, body),
  setActive: (id: string, active: boolean) =>
    http.put<CannedJob>(`/api/v1/canned-jobs/${id}/active`, { active }),
};

export const historyApi = {
  forVehicle: (vehicleId: string, params: Paging = {}) =>
    http.get<PageResponse<ServiceHistoryEntry>>(`/api/v1/service-history/vehicles/${vehicleId}`, {
      ...params,
    }),
};

// ---- auth-service ----------------------------------------------------------------------------

/** The signed-in user's own avatar. */
export const avatarApi = {
  choose: (body: { kind: 'INITIALS' | 'CHARACTER'; character?: string }) =>
    http.put<User>('/api/v1/auth/me/avatar', body),
  /** PNG or JPEG up to 2 MB (the page sends a square 512 px JPEG); the server re-encodes it. */
  upload: (file: Blob) => {
    const form = new FormData();
    form.append('file', file, 'avatar.jpg');
    return http.put<User>('/api/v1/auth/me/avatar/photo', form);
  },
  /** Workshop ADMIN: back to initials (e.g. an unsuitable photo). */
  reset: (userId: string) => http.delete<User>(`/api/v1/auth/users/${userId}/avatar`),
};

/** Customers' online accounts, from their page (advisors, managers, admins; customers' accounts only). */
export const customerAccountsApi = {
  get: (userId: string) => http.get<CustomerAccount>(`/api/v1/auth/customer-accounts/${userId}`),
  /** Status of several at once (a page of the customer list, at most 100). */
  statuses: (userIds: string[]) =>
    http.get<CustomerAccount[]>('/api/v1/auth/customer-accounts', { ids: userIds.join(',') }),
  /** Managers and admins: disabling signs them out everywhere. */
  setEnabled: (userId: string, enabled: boolean) =>
    http.patch<CustomerAccount>(`/api/v1/auth/customer-accounts/${userId}`, { enabled }),
  unlock: (userId: string) => http.post<CustomerAccount>(`/api/v1/auth/customer-accounts/${userId}/unlock`),
};

export const usersApi = {
  /** `kind`: staff (the Team page), customer, or all (default). */
  list: (params: Paging & { sort?: string; kind?: 'staff' | 'customer' | 'all' }) =>
    http.get<PageResponse<User>>('/api/v1/auth/users', { ...params }),
  create: (body: CreateStaffRequest) => http.post<User>('/api/v1/auth/users', body),
  setEnabled: (id: string, enabled: boolean) => http.patch<User>(`/api/v1/auth/users/${id}`, { enabled }),
  /** Everything a staff member does (ADMIN only); `locationIds` when they work at chosen locations. */
  setRoles: (id: string, body: { roles: Role[]; locationIds?: string[] }) =>
    http.put<User>(`/api/v1/auth/users/${id}/roles`, body),
  /** Where a MANAGER, SERVICE_ADVISOR or MECHANIC works (at least one location). */
  setLocations: (id: string, locationIds: string[]) =>
    http.put<User>(`/api/v1/auth/users/${id}/locations`, { locationIds }),
  /** ADMIN: anyone else; MANAGER and SERVICE_ADVISOR: customers. Returns the temporary password once. */
  resetPassword: (id: string) => http.post<TemporaryPassword>(`/api/v1/auth/users/${id}/password-reset`),
};

export const workshopApi = {
  /** The signed-in user's workshop and its settings (every role). */
  current: () => http.get<Workshop>('/api/v1/auth/tenant'),
  /** Workshop ADMIN only. */
  updateSettings: (body: UpdateWorkshopSettingsRequest) =>
    http.put<Workshop>('/api/v1/auth/tenant/settings', body),
};

/** The workshop's branding (workshop ADMIN only). Everyone sees it through tenantsApi.current. */
export const brandingApi = {
  get: () => http.get<Branding>('/api/v1/auth/tenant/branding'),
  update: (body: UpdateBrandingRequest) => http.put<Branding>('/api/v1/auth/tenant/branding', body),
  /** PNG or JPEG; logo up to 1 MB, cover up to 3 MB. The server re-encodes it. */
  upload: ({ kind, file }: { kind: BrandingImageKind; file: File }) => {
    const form = new FormData();
    form.append('file', file);
    return http.put<Branding>(`/api/v1/auth/tenant/branding/${kind}`, form);
  },
  remove: (kind: BrandingImageKind) => http.delete<Branding>(`/api/v1/auth/tenant/branding/${kind}`),
};

/** The workshop's announcements (workshop ADMIN only). Everyone sees the live ones via tenantsApi.current. */
export const announcementsApi = {
  list: () => http.get<Announcement[]>('/api/v1/auth/tenant/branding/announcements'),
  create: (body: AnnouncementRequest) =>
    http.post<Announcement>('/api/v1/auth/tenant/branding/announcements', body),
  update: ({ id, ...body }: AnnouncementRequest & { id: string; version: number }) =>
    http.put<Announcement>(`/api/v1/auth/tenant/branding/announcements/${id}`, body),
  remove: (id: string) => http.delete<void>(`/api/v1/auth/tenant/branding/announcements/${id}`),
};

/** The Ramplight console (PLATFORM_ADMIN at <base domain>/console). */
export const consoleApi = {
  demoRequests: (params: Paging & { status?: DemoStatus; q?: string }) =>
    http.get<PageResponse<ConsoleDemoRequest>>('/api/v1/auth/platform/demo-requests', { ...params }),
  updateDemoRequest: (id: string, body: { status: DemoStatus; notes: string | null; version: number }) =>
    http.put<ConsoleDemoRequest>(`/api/v1/auth/platform/demo-requests/${id}`, body),
  invitations: (params: Paging & { state?: InvitationState; q?: string }) =>
    http.get<PageResponse<ConsoleInvitation>>('/api/v1/auth/platform/invitations', { ...params }),
  createInvitation: (body: {
    email: string;
    contactName: string | null;
    shopName: string | null;
    demoRequestId: string | null;
  }) => http.post<IssuedInvitation>('/api/v1/auth/platform/invitations', body),
  resendInvitation: (id: string) =>
    http.post<IssuedInvitation>(`/api/v1/auth/platform/invitations/${id}/resend`),
  revokeInvitation: (id: string) =>
    http.post<ConsoleInvitation>(`/api/v1/auth/platform/invitations/${id}/revoke`),
  workshops: (params: Paging & { status?: ConsoleWorkshop['status']; test?: boolean; q?: string }) =>
    http.get<PageResponse<ConsoleWorkshop>>('/api/v1/auth/platform/workshops', { ...params }),
  /** A test workshop: no invite, labelled Test. */
  createTestWorkshop: (body: TestWorkshopRequest) =>
    http.post<ConsoleWorkshop>('/api/v1/auth/platform/workshops', body),
  suspendWorkshop: (id: string) => http.post<void>(`/api/v1/auth/platform/workshops/${id}/suspend`),
  reactivateWorkshop: (id: string) => http.post<void>(`/api/v1/auth/platform/workshops/${id}/reactivate`),
};

/** Public: sign up a workshop, check an address, name the workshop at this address. */
export const tenantsApi = {
  current: () => http.get<PublicWorkshop>('/api/v1/tenants/current'),
  availability: (slug: string) => http.get<SlugAvailability>('/api/v1/tenants/slug-availability', { slug }),
  signup: (body: SignupRequest) => http.post<SignupResponse>('/api/v1/tenants/signup', body),
  /** Whether a setup link works, and who it is for (400 invite-expired / invite-used / invite-invalid). */
  checkInvite: (token: string) => http.post<InviteDetails>('/api/v1/tenants/invitations/check', { token }),
  /** "Request a demo" on the public site (rate-limited per IP). */
  requestDemo: (body: DemoRequest) => http.post<{ received: boolean }>('/api/v1/tenants/demo-requests', body),
};

/** The organization's locations (every signed-in user). Shown in the app from phase L3. */
export const locationsApi = {
  list: () => http.get<Location[]>('/api/v1/auth/locations'),
  /** ADMIN only. Empty timeZone / labourRate = use the organization's. */
  create: (body: LocationRequest) => http.post<Location>('/api/v1/auth/locations', body),
  update: (id: string, body: LocationRequest & { version: number }) =>
    http.put<Location>(`/api/v1/auth/locations/${id}`, body),
  archive: (id: string) => http.post<Location>(`/api/v1/auth/locations/${id}/archive`),
  /** ADMIN: tax rates, fees and invoice terms; send the location's version. */
  updateBilling: (id: string, billing: LocationBilling, version: number) =>
    http.put<Location>(`/api/v1/auth/locations/${id}/billing`, { billing, version }),
  reactivate: (id: string) => http.post<Location>(`/api/v1/auth/locations/${id}/reactivate`),
};

/** The signed-in user's own notifications (notification-service); read ones are deleted. */
export const notificationsApi = {
  /** All unread: reading a notification deletes it. */
  list: (page = 0, size = 20) =>
    http.get<PageResponse<AppNotification>>('/api/v1/notifications', { page, size }),
  unreadCount: () => http.get<{ count: number }>('/api/v1/notifications/unread-count'),
  /** Deletes it (204). */
  markRead: (id: string) => http.post<void>(`/api/v1/notifications/${id}/read`),
  markAllRead: () => http.post<{ count: number }>('/api/v1/notifications/read-all'),
};

/** A report period: ISO instants, from inclusive, to exclusive. */
export interface ReportRange {
  from: string;
  to: string;
}

/** Owner dashboard and reports (ADMIN, MANAGER), for the locations in view. */
export const reportsApi = {
  summary: (range: ReportRange) => http.get<ReportSummary>('/api/v1/reports/summary', { ...range }),
  daily: (range: ReportRange, zone?: string) =>
    http.get<DailyReport>('/api/v1/reports/daily', { ...range, zone }),
  technicians: (range: ReportRange) => http.get<TechnicianRow[]>('/api/v1/reports/technicians', { ...range }),
  advisors: (range: ReportRange) => http.get<AdvisorRow[]>('/api/v1/reports/advisors', { ...range }),
  locations: (range: ReportRange) => http.get<LocationSalesRow[]>('/api/v1/reports/locations', { ...range }),
  declined: (range: ReportRange) =>
    http.get<ReportRows<DeclinedRow>>('/api/v1/reports/declined', { ...range }),
  parts: (range: ReportRange) => http.get<ReportRows<PartUsageRow>>('/api/v1/reports/parts', { ...range }),
  payments: (range: ReportRange) => http.get<PaymentsReport>('/api/v1/reports/payments', { ...range }),
  /** Now, not for a period. */
  unpaid: () => http.get<UnpaidReport>('/api/v1/reports/unpaid'),
  tax: (range: ReportRange) => http.get<TaxReport>('/api/v1/reports/tax', { ...range }),
};

export interface InvoiceFilters extends Paging {
  /** DRAFT, ISSUED, PARTIALLY_PAID, PAID, VOID, or UNPAID (issued or partly paid). */
  status?: string;
  from?: string;
  to?: string;
  /** Number, customer or registration. */
  q?: string;
}

/** Invoices (workshop-service). Customers get their own issued ones. */
export const quickbooksApi = {
  status: () => http.get<QuickBooksStatus>('/api/v1/quickbooks'),
  /** Intuit's sign-in address; Intuit sends the browser back to returnUrl. */
  connect: (returnUrl: string) =>
    http.post<{ authorizeUrl: string }>('/api/v1/quickbooks/connect', { returnUrl }),
  disconnect: () => http.delete<void>('/api/v1/quickbooks/connection'),
  items: () => http.get<QuickBooksChoice[]>('/api/v1/quickbooks/items'),
  accounts: () => http.get<QuickBooksChoice[]>('/api/v1/quickbooks/accounts'),
  saveMapping: (mapping: QuickBooksMapping) =>
    http.put<QuickBooksStatus>('/api/v1/quickbooks/mapping', mapping),
};

export const invoicesApi = {
  list: (filters: InvoiceFilters) =>
    http.get<PageResponse<InvoiceSummary>>('/api/v1/invoices', { ...filters }),
  get: (id: string) => http.get<Invoice>(`/api/v1/invoices/${id}`),
  /** 404 when the job has none (or, for a customer, only a draft). */
  forJobCard: (jobCardId: string) => http.get<Invoice>(`/api/v1/job-cards/${jobCardId}/invoice`),
  create: (jobCardId: string) => http.post<Invoice>(`/api/v1/job-cards/${jobCardId}/invoice`),
  update: (id: string, body: { lines: InvoiceLineRequest[]; notes: string | null; version: number }) =>
    http.put<Invoice>(`/api/v1/invoices/${id}`, body),
  discard: (id: string) => http.delete<void>(`/api/v1/invoices/${id}`),
  issue: (id: string, version: number) => http.post<Invoice>(`/api/v1/invoices/${id}/issue`, { version }),
  /** The key makes a retried or double-clicked payment count once. */
  pay: (id: string, payment: PaymentRequest, idempotencyKey: string) =>
    request<Invoice>(`/api/v1/invoices/${id}/payments`, {
      method: 'POST',
      body: payment,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  void: (id: string, reason: string) => http.post<Invoice>(`/api/v1/invoices/${id}/void`, { reason }),
  /** Issued invoices and payments of [from, to), for CSV files (managers and admins). */
  export: (from: string, to: string) => http.get<InvoiceExport>('/api/v1/invoices/export', { from, to }),
  /** Sends what QuickBooks doesn't have yet; a QuickBooks error comes back in quickbooks.error. */
  sendToQuickBooks: (id: string) => http.post<Invoice>(`/api/v1/invoices/${id}/quickbooks`),
  /** to: null = the customer's address on the invoice. */
  email: (id: string, to: string | null) => http.post<Invoice>(`/api/v1/invoices/${id}/email`, { to }),
};
