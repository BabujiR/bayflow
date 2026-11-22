/**
 * The API's request and response types, built on the types generated from the services'
 * OpenAPI documents (./schema, regenerate with `pnpm api:update`).
 *
 * The generated response types mark every field optional. {@link Present} marks the fields
 * the backend always sends as required, so only genuinely optional fields (like a
 * job card id before check-in) need checks in the UI.
 */
import type { components as AppointmentApi } from './schema/appointment';
import type { components as AuthApi } from './schema/auth';
import type { components as WorkshopApi } from './schema/workshop';

type A = AuthApi['schemas'];
type P = AppointmentApi['schemas'];
type W = WorkshopApi['schemas'];

/** T with keys K required and non-null. */
type Present<T, K extends keyof T> = Omit<T, K> & { [Key in K]-?: NonNullable<T[Key]> };

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---- auth --------------------------------------------------------------------------

/** MANAGER (phase L2) is hand-written until `pnpm api:update`. */
export type Role = A['CreateStaffRequest']['role'] | 'MANAGER';

export type User = Present<
  Omit<A['UserResponse'], 'roles'>,
  'id' | 'username' | 'email' | 'enabled' | 'createdAt'
> & {
  roles: Role[];
  /** Works with every location (ADMIN, CUSTOMER); otherwise at locationIds. Hand-written (L2). */
  allLocations?: boolean;
  locationIds?: string[];
  /** Signed in with a temporary password from a staff reset: must choose a new one first. */
  mustChangePassword?: boolean;
};

/** A staff password reset's result: shown once, then gone. */
export interface TemporaryPassword {
  userId: string;
  username: string;
  temporaryPassword: string;
  expiresAt: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  /** From the e-mailed link. */
  token: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  /** The temporary password after a reset. */
  currentPassword: string;
  newPassword: string;
}

export type TokenResponse = Present<
  Omit<A['TokenResponse'], 'user'>,
  'accessToken' | 'tokenType' | 'expiresIn' | 'expiresAt'
> & { user: User };

export type LoginRequest = A['LoginRequest'];
export type RegisterRequest = A['RegisterRequest'];
export type CreateStaffRequest = Omit<A['CreateStaffRequest'], 'role'> & {
  role: Role;
  /** For MANAGER, SERVICE_ADVISOR and MECHANIC; none = the organization's only location. */
  locationIds?: string[];
};

// ---- appointments, customers, vehicles ------------------------------------------------

export type ServiceType = P['BookAppointmentRequest']['serviceType'];
export type AppointmentStatus = NonNullable<P['AppointmentResponse']['status']>;

export type Customer = Present<P['CustomerResponse'], 'id' | 'name' | 'email' | 'createdAt'>;

export type Vehicle = Present<
  P['VehicleResponse'],
  'id' | 'customerId' | 'vin' | 'registration' | 'make' | 'model' | 'year' | 'mileage' | 'createdAt'
>;

export type VehicleSummary = Present<
  P['VehicleSummary'],
  'id' | 'vin' | 'registration' | 'make' | 'model' | 'year'
>;

export type Appointment = Present<
  Omit<P['AppointmentResponse'], 'vehicle'>,
  | 'id'
  | 'customerId'
  | 'customerName'
  | 'scheduledAt'
  | 'endsAt'
  | 'durationMinutes'
  | 'serviceType'
  | 'status'
  | 'createdAt'
  | 'updatedAt'
> & {
  vehicle: VehicleSummary;
  /** The location (shop) of the organization; hand-written until `pnpm api:update`. */
  locationId: string;
};

export type ProfileRequest = P['ProfileRequest'];
export type CreateCustomerRequest = P['CreateCustomerRequest'];
export type CreateVehicleRequest = P['CreateVehicleRequest'];
export type UpdateVehicleRequest = P['UpdateVehicleRequest'];
export type BookAppointmentRequest = P['BookAppointmentRequest'] & {
  /** Optional while the organization has one location; hand-written until `pnpm api:update`. */
  locationId?: string;
};
export type RescheduleRequest = P['RescheduleRequest'];

// ---- workshop -----------------------------------------------------------------------

export type JobCardStatus = NonNullable<W['JobCardResponse']['status']>;
export type Severity = W['FindingRequest']['severity'];
export type ApprovalStatus = NonNullable<W['RecommendationResponse']['approvalStatus']>;

export type Finding = Present<
  W['FindingResponse'],
  'id' | 'description' | 'severity' | 'recordedBy' | 'createdAt'
>;

export type Recommendation = Present<
  W['RecommendationResponse'],
  | 'id'
  | 'description'
  | 'estimatedPartsCost'
  | 'estimatedLabourHours'
  | 'estimatedLabourCost'
  | 'estimatedTotal'
  | 'approvalStatus'
>;

export type WorkLog = Present<
  W['WorkLogResponse'],
  'id' | 'recommendationId' | 'mechanic' | 'description' | 'completedAt'
>;

export type Part = Present<
  W['PartResponse'],
  'id' | 'recommendationId' | 'partNumber' | 'name' | 'quantity' | 'unitPrice' | 'lineTotal' | 'recordedBy'
>;

export type Labour = Present<
  W['LabourResponse'],
  'id' | 'recommendationId' | 'mechanic' | 'hours' | 'hourlyRate' | 'cost'
>;

export type JobCard = Present<
  Omit<W['JobCardResponse'], 'findings' | 'recommendations' | 'workLogs' | 'parts' | 'labour'>,
  | 'id'
  | 'appointmentId'
  | 'vehicleId'
  | 'vin'
  | 'registration'
  | 'customerName'
  | 'mileage'
  | 'status'
  | 'labourRate'
  | 'currency'
  | 'estimatedTotal'
  | 'approvedEstimate'
  | 'createdAt'
  | 'updatedAt'
> & {
  findings: Finding[];
  recommendations: Recommendation[];
  workLogs: WorkLog[];
  parts: Part[];
  labour: Labour[];
  /** The location (shop) where the work is done; hand-written until `pnpm api:update`. */
  locationId: string;
};

export type JobCardSummary = Present<
  W['JobCardSummary'],
  | 'id'
  | 'appointmentId'
  | 'vehicleId'
  | 'vin'
  | 'registration'
  | 'customerName'
  | 'mileage'
  | 'status'
  | 'createdAt'
  | 'updatedAt'
> & {
  /** Hand-written until `pnpm api:update`. */
  locationId?: string;
};

export type ServiceRecord = W['ServiceRecord'];

export type ServiceHistoryEntry = Present<
  W['ServiceHistoryResponse'],
  | 'id'
  | 'vehicleId'
  | 'jobCardId'
  | 'vin'
  | 'registration'
  | 'serviceDate'
  | 'mileage'
  | 'partsTotal'
  | 'labourTotal'
  | 'totalCost'
  | 'currency'
> & {
  /** Where the work was done; hand-written until `pnpm api:update`. */
  locationId?: string;
};

export type FindingRequest = W['FindingRequest'];
export type RecommendationRequest = W['RecommendationRequest'];
export type WorkLogRequest = W['WorkLogRequest'];
export type PartRequest = W['PartRequest'];
export type LabourRequest = W['LabourRequest'];

// ---- workshop settings (auth-service, SaaS phase 3) --------------------------------------
// Written by hand until the schema is regenerated with `pnpm api:update`.

/** The signed-in user's workshop and its settings. */
export interface Workshop {
  id: string;
  slug: string;
  name: string;
  displayName: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  labourRate: number;
  /** ISO 4217 code, e.g. USD. */
  currency: string;
  /** IANA time zone, e.g. America/New_York. Defines the workshop's "today". */
  timeZone: string;
  /** Send back unchanged with an update; 409 if someone saved in between. */
  version: number;
}

export interface UpdateWorkshopSettingsRequest {
  displayName: string;
  phone?: string;
  email?: string;
  address?: string;
  labourRate: number;
  currency: string;
  timeZone: string;
  version: number;
}

// ---- workshops: sign-up and addresses (auth-service, SaaS phase 4) -----------------------

/** The workshop at this address, for its sign-in page (public). */
export interface PublicWorkshop {
  slug: string;
  name: string;
  displayName: string;
  /** False while the workshop is suspended: nobody can sign in. */
  active: boolean;
  /** The workshop's look (Branding). Missing from older servers: then Bayflow's look. */
  branding?: PublicBranding;
  /** E-mail is set up: the sign-in page offers "Forgot password?". */
  passwordResetByEmail?: boolean;
}

// ---- branding (auth-service) -------------------------------------------------------------

export type AnnouncementStyle = 'INFO' | 'IMPORTANT';

/** A live announcement, as everyone at the workshop's address sees it. */
export interface PublicAnnouncement {
  id: string;
  text: string;
  style: AnnouncementStyle;
}

/** What every visitor of a workshop's address sees. */
export interface PublicBranding {
  logoUrl: string | null;
  coverUrl: string | null;
  /** For dark mode; none = logoUrl in both modes. Missing from older servers. */
  logoDarkUrl?: string | null;
  /** Square: browser tab, collapsed sidebar, phone header. Missing from older servers. */
  iconUrl?: string | null;
  /** #rrggbb, or null for Bayflow's colours. */
  primaryColor: string | null;
  showPoweredBy: boolean;
  /** The live ones only, in the order shown (important first); empty when there are none. */
  announcements: PublicAnnouncement[];
}

/** The workshop ADMIN's view of the branding (announcements: below). */
export interface Branding {
  primaryColor: string | null;
  showPoweredBy: boolean;
  logoUrl: string | null;
  coverUrl: string | null;
  logoDarkUrl?: string | null;
  iconUrl?: string | null;
  version: number;
}

export interface UpdateBrandingRequest {
  primaryColor: string | null;
  showPoweredBy: boolean;
  version: number;
}

/** One of the workshop's announcements, as its ADMIN keeps them (up to 10). */
export interface Announcement {
  id: string;
  text: string;
  style: AnnouncementStyle;
  startsAt: string | null;
  endsAt: string | null;
  /** Where it is in its dates right now. */
  state: 'LIVE' | 'SCHEDULED' | 'ENDED';
  createdAt: string;
  version: number;
}

export interface AnnouncementRequest {
  text: string;
  style: AnnouncementStyle;
  startsAt: string | null;
  endsAt: string | null;
}

export type BrandingImageKind = 'logo' | 'logo-dark' | 'icon' | 'cover';

export interface SignupRequest {
  workshopName: string;
  slug: string;
  labourRate?: number;
  currency?: string;
  timeZone?: string;
  adminUsername: string;
  adminEmail: string;
  adminPassword: string;
}

export interface SignupResponse {
  id: string;
  slug: string;
  name: string;
  adminUsername: string;
}

export interface SlugAvailability {
  slug: string;
  available: boolean;
  reason?: 'taken' | 'reserved' | 'invalid' | null;
}

// ---- organizations and locations (auth-service, phase L1) -----------------------------------

/** A location (shop) of the organization, with the settings that apply to it. */
export interface Location {
  id: string;
  code: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  /** Effective time zone: the location's own, else the organization's. */
  timeZone: string;
  /** Effective labour rate: the location's own, else the organization's. */
  labourRate: number;
  /** The organization's currency. */
  currency: string;
  /** The location's own override; null = uses the organization's. */
  ownTimeZone?: string | null;
  ownLabourRate?: number | null;
  version: number;
  /** Tax, fees and invoice terms (R2a). Missing from older servers. */
  billing?: LocationBilling;
}

/**
 * A location's invoice settings: tax rates in percent (8.875 = 8.875 %), fees (null = off),
 * paymentTermsDays (0 = due on receipt). Copied onto each invoice when it is created.
 */
export interface LocationBilling {
  partsTaxRate: number;
  labourTaxRate: number;
  feesTaxRate: number;
  shopSuppliesPercent: number | null;
  shopSuppliesCap: number | null;
  disposalFee: number | null;
  paymentTermsDays: number;
  invoiceFooter: string | null;
}

/** What an admin sets on a location; empty timeZone / labourRate = use the organization's. */
export interface LocationRequest {
  code: string;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  timeZone?: string;
  labourRate?: number;
  version?: number;
}

// ---- notifications (notification-service, phase N1) --------------------------------------

export type NotificationType =
  | 'APPOINTMENT_BOOKED'
  | 'APPOINTMENT_RESCHEDULED'
  | 'APPOINTMENT_CANCELLED'
  | 'APPOINTMENT_CONFIRMED'
  | 'VEHICLE_CHECKED_IN'
  | 'ESTIMATE_READY'
  | 'ESTIMATE_DECIDED'
  | 'WORK_COMPLETED'
  | 'INVOICE_ISSUED';

/** One unread message to the signed-in user (reading it deletes it). Title and body are plain text. */
export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  /** A path in this app, e.g. /job-cards/<id>. */
  link: string | null;
  locationId: string | null;
  createdAt: string;
}

// ---- reports (workshop-service, phase R1) -----------------------------------------------
// Hand-written until `pnpm api:update`. Money is in the workshop's currency; ratios are 0–1.

export interface PeriodMetrics {
  carCount: number;
  sales: number;
  partsSales: number;
  labourSales: number;
  averageRepairOrder: number;
  labourHours: number;
  effectiveLabourRate: number;
  itemsApproved: number;
  itemsDeclined: number;
  approvedValue: number;
  declinedValue: number;
  hoursApproved: number;
  hoursDeclined: number;
  /** Approved ÷ (approved + declined) value; null when nothing was decided. */
  closeRatio: number | null;
}

export interface OpenWork {
  openCards: number;
  awaitingApproval: number;
  inWork: number;
  pendingValue: number;
}

export interface ReportSummary {
  from: string;
  to: string;
  current: PeriodMetrics;
  previousFrom: string;
  previousTo: string;
  previous: PeriodMetrics;
  open: OpenWork;
}

export interface DailySales {
  /** YYYY-MM-DD in the requested zone. */
  day: string;
  carCount: number;
  sales: number;
  partsSales: number;
  labourSales: number;
}

export interface DailyReport {
  from: string;
  to: string;
  zone: string;
  days: DailySales[];
}

export interface TechnicianRow {
  mechanic: string;
  hours: number;
  labourValue: number;
  jobCards: number;
  hoursOnCompleted: number;
}

export interface AdvisorRow {
  /** Empty for estimates sent before reports existed. */
  advisor: string;
  estimatesSent: number;
  presentedValue: number;
  approvedValue: number;
  declinedValue: number;
  closeRatio: number | null;
  completedCards: number;
  completedSales: number;
  averageRepairOrder: number;
}

export interface LocationSalesRow {
  locationId: string;
  carCount: number;
  sales: number;
  partsSales: number;
  labourSales: number;
  averageRepairOrder: number;
  labourHours: number;
}

export interface DeclinedRow {
  itemId: string;
  jobCardId: string;
  locationId: string;
  customerUserId: string | null;
  customerName: string;
  registration: string;
  vin: string;
  description: string;
  estimatedPartsCost: number;
  estimatedLabourHours: number;
  value: number;
  decidedBy: string | null;
  decidedAt: string;
  submittedBy: string | null;
}

export interface PartUsageRow {
  id: string;
  jobCardId: string;
  locationId: string;
  partNumber: string;
  name: string;
  quantity: number;
  unitPrice: number;
  total: number;
  recordedBy: string;
  recordedAt: string;
  registration: string;
  customerName: string;
  recommendation: string | null;
}

export interface ReportRows<T> {
  from: string;
  to: string;
  rows: T[];
  /** More rows matched than the 1000 returned. */
  truncated: boolean;
}

// ---- invoices (workshop-service, phase R2a) ----------------------------------------------
// Hand-written until `pnpm api:update`. Money is in the invoice's currency.

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'VOID';
export type InvoiceLineType = 'PART' | 'LABOUR' | 'FEE' | 'DISCOUNT';
export type TaxCategory = 'PARTS' | 'LABOUR' | 'FEES';
export type PaymentMethod = 'CASH' | 'CARD' | 'CHECK' | 'BANK_TRANSFER' | 'OTHER';

export interface InvoiceLine {
  id: string;
  type: InvoiceLineType;
  /** SHOP_SUPPLIES (follows labour) or DISPOSAL; null otherwise. */
  feeCode: string | null;
  description: string;
  partNumber: string | null;
  quantity: number;
  unitPrice: number;
  /** What the shop paid (parts); staff only. */
  unitCost: number | null;
  /** Negative for a discount. */
  lineTotal: number;
  taxable: boolean;
  taxCategory: TaxCategory;
  /** Parts and labour from the job card: repriced, never removed. */
  fromJobCard: boolean;
}

export interface InvoicePayment {
  id: string;
  method: PaymentMethod;
  /** Negative for a refund. */
  amount: number;
  reference: string | null;
  receivedAt: string;
  receivedBy: string;
}

export interface InvoiceTax {
  category: TaxCategory;
  /** Percent. */
  rate: number;
  tax: number;
}

export interface Invoice {
  id: string;
  number: string | null;
  status: InvoiceStatus;
  jobCardId: string;
  locationId: string;
  customerUserId: string | null;
  customerName: string;
  customerEmail: string | null;
  customerPhone: string | null;
  registration: string;
  vin: string;
  mileage: number;
  vehicleDescription: string | null;
  currency: string;
  lines: InvoiceLine[];
  partsTotal: number;
  labourTotal: number;
  feesTotal: number;
  discountTotal: number;
  subtotal: number;
  taxes: InvoiceTax[];
  taxTotal: number;
  total: number;
  amountPaid: number;
  balanceDue: number;
  payments: InvoicePayment[];
  notes: string | null;
  footer: string | null;
  paymentTermsDays: number;
  issuedAt: string | null;
  issuedBy: string | null;
  dueAt: string | null;
  voidedAt: string | null;
  voidedBy: string | null;
  voidReason: string | null;
  createdAt: string;
  createdBy: string;
  /** Staff only: profit on the parts that have a cost entered. */
  partsProfit: { partsWithCost: number; partsCost: number; profit: number } | null;
  version: number;
}

export interface InvoiceSummary {
  id: string;
  number: string | null;
  status: InvoiceStatus;
  jobCardId: string;
  locationId: string;
  customerName: string;
  registration: string;
  currency: string;
  total: number;
  balanceDue: number;
  issuedAt: string | null;
  dueAt: string | null;
  createdAt: string;
}

/** One line of a draft as edited; id omitted = a new fee or discount. */
export interface InvoiceLineRequest {
  id?: string;
  type?: InvoiceLineType;
  description: string;
  partNumber?: string | null;
  quantity: number;
  unitPrice: number;
  unitCost?: number | null;
  taxable?: boolean;
  taxCategory?: TaxCategory;
}

export interface PaymentRequest {
  method: PaymentMethod;
  /** Negative = refund (managers and admins). */
  amount: number;
  reference?: string;
}
