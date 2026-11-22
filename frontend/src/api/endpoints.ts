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
  LocationBilling,
  PaymentRequest,
  Location,
  LocationRequest,
  LocationSalesRow,
  LabourRequest,
  PageResponse,
  PartRequest,
  PartUsageRow,
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
  TechnicianRow,
  TemporaryPassword,
  UpdateBrandingRequest,
  UpdateVehicleRequest,
  UpdateWorkshopSettingsRequest,
  User,
  Vehicle,
  WorkLogRequest,
  Workshop,
} from './types';

export interface Paging {
  page?: number;
  size?: number;
}

// ---- appointment-service ---------------------------------------------------------------

export const customersApi = {
  me: () => http.get<Customer>('/api/v1/customers/me'),
  createMe: (body: ProfileRequest) => http.post<Customer>('/api/v1/customers/me', body),
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
  checkIn: (id: string, mileage: number) =>
    http.post<Appointment>(`/api/v1/appointments/${id}/check-in`, { mileage }),
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
};

export const historyApi = {
  forVehicle: (vehicleId: string, params: Paging = {}) =>
    http.get<PageResponse<ServiceHistoryEntry>>(`/api/v1/service-history/vehicles/${vehicleId}`, {
      ...params,
    }),
};

// ---- auth-service ----------------------------------------------------------------------------

export const usersApi = {
  list: (params: Paging & { sort?: string }) =>
    http.get<PageResponse<User>>('/api/v1/auth/users', { ...params }),
  create: (body: CreateStaffRequest) => http.post<User>('/api/v1/auth/users', body),
  setEnabled: (id: string, enabled: boolean) => http.patch<User>(`/api/v1/auth/users/${id}`, { enabled }),
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

/** Public: sign up a workshop, check an address, name the workshop at this address. */
export const tenantsApi = {
  current: () => http.get<PublicWorkshop>('/api/v1/tenants/current'),
  availability: (slug: string) => http.get<SlugAvailability>('/api/v1/tenants/slug-availability', { slug }),
  signup: (body: SignupRequest) => http.post<SignupResponse>('/api/v1/tenants/signup', body),
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
};
