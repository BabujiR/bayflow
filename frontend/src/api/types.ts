/**
 * The API's request and response types, built on the types generated from the services'
 * OpenAPI documents (./schema, regenerate with `pnpm api:update`).
 *
 * The generated response types mark every field optional. {@link Present} marks the fields
 * the backend always sends as required, so only genuinely optional fields (like a
 * job card id before check-in) need checks in the UI. Types still written by hand are checked
 * against the generated ones in ./schema-drift.ts: a field the API drops fails type-checking.
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

export type Role = NonNullable<A['CreateStaffRequest']['role']>;

/**
 * `allLocations`: works with every location (ADMIN, CUSTOMER), otherwise at `locationIds`.
 * `mustChangePassword`: signed in with a temporary password from a staff reset.
 */
export type User = Present<
  Omit<A['UserResponse'], 'roles' | 'avatar'>,
  'id' | 'username' | 'email' | 'enabled' | 'createdAt'
> & {
  /** The schema says string[]; they are always roles. */
  roles: Role[];
  /** How they show in the app (initials when missing). */
  avatar?: Avatar;
};

/** A customer's online account as staff see it on the customer's page. */
export interface CustomerAccount {
  id: string;
  username: string;
  email: string;
  enabled: boolean;
  /** Too many failed sign-ins: locked until `lockedUntil` (or until unlocked). */
  locked: boolean;
  lockedUntil: string | null;
  /** Has a temporary password from a reset, not yet replaced. */
  mustChangePassword: boolean;
  createdAt: string;
}

export type AvatarKind = 'INITIALS' | 'CHARACTER' | 'PHOTO';

/** INITIALS (from the username), a built-in CHARACTER (`character`), or their PHOTO (`photoUrl`). */
export interface Avatar {
  kind: AvatarKind;
  character: string | null;
  photoUrl: string | null;
}

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
export type CreateStaffRequest = Omit<A['CreateStaffRequest'], 'role' | 'roles'> & {
  /** Everything they do, e.g. ['ADMIN', 'MECHANIC'] (the API also takes a single `role`). */
  roles: Role[];
  /** For MANAGER, SERVICE_ADVISOR and MECHANIC; none = the organization's only location. */
  locationIds?: string[];
};

// ---- appointments, customers, vehicles ------------------------------------------------

export type ServiceType = P['BookAppointmentRequest']['serviceType'];
/** ESTIMATE: a quick estimate whose visit has no time yet. */
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
  Omit<P['AppointmentResponse'], 'vehicle' | 'serviceName' | 'cancelReason'>,
  | 'id'
  | 'locationId'
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
  /** The service they picked online (`bookedOnline`), e.g. "Oil change" or "Something else". */
  serviceName?: string | null;
  /** The customer's reason, when they cancelled from their booking's private link. */
  cancelReason?: string | null;
};

export type ProfileRequest = P['ProfileRequest'];
export type CreateCustomerRequest = P['CreateCustomerRequest'];
/** `trim`, `engine`: optional; on an update, leaving one out keeps it and '' clears it. */
export type CreateVehicleRequest = P['CreateVehicleRequest'];
export type UpdateVehicleRequest = P['UpdateVehicleRequest'];
/** `locationId`: optional while the organization has one location. */
export type BookAppointmentRequest = P['BookAppointmentRequest'];
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
  Omit<W['JobCardResponse'], 'findings' | 'recommendations' | 'workLogs' | 'parts' | 'labour' | 'arrivedAt'>,
  | 'id'
  | 'appointmentId'
  | 'locationId'
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
  /** When the car came in; empty for a quick estimate before its visit. */
  arrivedAt?: string | null;
};

export type JobCardSummary = Present<
  Omit<W['JobCardSummary'], 'arrivedAt'>,
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
  /** Empty for a quick estimate before its visit. */
  arrivedAt?: string | null;
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
>;

export type FindingRequest = W['FindingRequest'];
export type RecommendationRequest = W['RecommendationRequest'];
export type WorkLogRequest = W['WorkLogRequest'];
export type PartRequest = W['PartRequest'];
export type LabourRequest = W['LabourRequest'];

// ---- canned jobs (R3a) ---------------------------------------------------------------------
// Hand-written (stricter than the generated types); schema-drift.ts checks them against the API.

export interface CannedJobPart {
  id: string;
  partNumber: string;
  name: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** A ready-made package of work; locationId null = every location. */
export interface CannedJob {
  id: string;
  locationId: string | null;
  name: string;
  category: string | null;
  description: string;
  labourHours: number;
  parts: CannedJobPart[];
  /** What the usual parts come to: the parts estimate of a recommendation. */
  partsTotal: number;
  active: boolean;
  updatedAt: string;
  version: number;
}

export interface CannedJobRequest {
  locationId: string | null;
  name: string;
  category: string | null;
  description: string;
  labourHours: number;
  parts: { partNumber: string; name: string; quantity: number; unitPrice: number }[];
  /** Required to update: the version read. */
  version: number | null;
}

// ---- maintenance reminders (R3b) -----------------------------------------------------------

/** The shop's rule: every miles or months, whichever first; matched by canned job or keyword. */
export interface ServiceInterval {
  id: string;
  name: string;
  miles: number | null;
  months: number | null;
  keywords: string[];
  cannedJobIds: string[];
  active: boolean;
  version: number;
}

export interface ServiceIntervalRequest {
  name: string;
  miles: number | null;
  months: number | null;
  keywords: string[];
  cannedJobIds: string[];
  active: boolean;
  version: number | null;
}

export interface ReminderSettings {
  enabled: boolean;
  remindDaysBefore: number;
  milesPerDay: number;
  version: number;
}

/** One service of a vehicle and when it is next due. */
export interface DueService {
  intervalId: string;
  name: string;
  lastDoneAt: string;
  lastMileage: number;
  dueMileage: number | null;
  dueAt: string;
  overdue: boolean;
  /** A canned job that counts as this service, to recommend it with. */
  cannedJobId: string | null;
}

export interface DueVehicle {
  vehicleId: string;
  registration: string;
  customerName: string;
  customerUserId: string | null;
  locationId: string;
  dueAt: string;
  remindedAt: string | null;
  services: DueService[];
}

/** Several parts and the labour for one approved item at once; hours null = no labour. */
export interface WorkRequest {
  parts: { partNumber: string; name: string; quantity: number; unitPrice: number }[];
  hours: number | null;
}

// ---- workshop settings (auth-service, SaaS phase 3) --------------------------------------
// Hand-written (stricter than the generated types); schema-drift.ts checks them against the API.

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

/** A demo request as the Ramplight console sees it. */
export type DemoStatus =
  'NEW' | 'CONTACTED' | 'DEMO_SCHEDULED' | 'DEMO_DONE' | 'INVITED' | 'ONBOARDED' | 'DECLINED';

export interface ConsoleDemoRequest {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  shopName: string;
  /** Older requests only: the form no longer asks. */
  shopType: ShopType | null;
  locations: LocationCount;
  city: string | null;
  message: string | null;
  status: DemoStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string | null;
  updatedBy: string | null;
  version: number;
  /** Times the same email asked again while this was open (their new details are in the notes). */
  repeatCount: number;
  /** When they last asked (null: only once). */
  lastRequestedAt: string | null;
}

/** How a bookable service's price shows: "from $54", "$79", "Free", "Ask for a quote", or nothing. */
export type PriceKind = 'NONE' | 'FROM' | 'FIXED' | 'FREE' | 'QUOTE';

/** A service customers can book online (Settings → Online booking). */
export interface BookingServiceItem {
  id: string;
  /** Stable link name, e.g. "oil-change" (/book?service=oil-change). */
  code: string;
  name: string;
  description: string | null;
  priceKind: PriceKind;
  price: number | null;
  durationMinutes: number | null;
  /** Null: every location. */
  locationId: string | null;
  active: boolean;
  sortOrder: number;
  updatedAt: string;
  version: number;
}

export interface BookingServiceRequest {
  name: string;
  description: string | null;
  priceKind: PriceKind;
  price: number | null;
  durationMinutes: number | null;
  locationId: string | null;
  active: boolean;
  version?: number;
}

/** One day's opening hours for online booking ("08:00"); both null: closed. */
export interface DayHours {
  opens: string | null;
  closes: string | null;
}

/** Settings → Online booking: when a location takes online bookings. Days run Monday to Sunday. */
export interface BookingHours {
  locationId: string;
  days: DayHours[];
  slotMinutes: number;
  carsPerSlot: number;
  minNoticeMinutes: number;
  maxDaysAhead: number;
  /** 0 until first saved (the defaults are shown). */
  version: number;
}

export type BookingHoursRequest = Omit<BookingHours, 'locationId'>;

/** The public booking page (no sign-in): what the workshop at this address offers. */
export interface PublicBookingOptions {
  shopName: string;
  currency: string;
  locations: { code: string; name: string; address: string | null; timeZone: string }[];
  services: {
    code: string;
    name: string;
    description: string | null;
    priceKind: PriceKind;
    price: number | null;
    durationMinutes: number | null;
    /** Location codes offering it; null: all. */
    locations: string[] | null;
  }[];
}

export interface PublicAvailability {
  location: string;
  timeZone: string;
  /** From the asked day; `slots` are start times (ISO instants), empty when closed or full. */
  days: { date: string; slots: string[] }[];
}

export interface PublicBookingRequest {
  location: string;
  /** Null: "Something else" (then `description`). */
  service: string | null;
  description: string | null;
  vin: string | null;
  year: number | null;
  make: string | null;
  model: string | null;
  startsAt: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  smsConsent: boolean;
  /** Stays empty: only bots fill it in. */
  website: string;
}

export interface PublicBooked {
  startsAt: string;
  timeZone: string;
  locationName: string;
  serviceName: string;
  email: string;
  /** A confirmation e-mail is on its way. */
  emailed: boolean;
  /** The private "change or cancel" link's token (null for a request the server ignored). */
  manageToken: string | null;
}

/** Why an online booking can't be changed from its link (NONE: it can). */
export type ManageBlocked = 'NONE' | 'CANCELLED' | 'ARRIVED' | 'PAST' | 'TOO_LATE' | 'LOCATION_CLOSED';

/** An online booking as its private link shows it (/book/manage?token=…). */
export interface ManagedBooking {
  status: AppointmentStatus;
  startsAt: string;
  timeZone: string;
  durationMinutes: number;
  locationCode: string | null;
  locationName: string | null;
  address: string | null;
  manyLocations: boolean;
  serviceName: string;
  car: string;
  shopName: string;
  firstName: string;
  canChange: boolean;
  blocked: ManageBlocked;
  noticeMinutes: number;
  reschedulesLeft: number;
  cancelledByCustomer: boolean;
  /** Already in a customer account. */
  hasAccount: boolean;
}

/** A setup invite as the console shows it (Pending turns Expired after 7 days). */
export type InvitationState = 'PENDING' | 'EXPIRED' | 'ACCEPTED' | 'REVOKED';

export interface ConsoleInvitation {
  id: string;
  email: string;
  contactName: string | null;
  shopName: string | null;
  demoRequestId: string | null;
  state: InvitationState;
  expiresAt: string;
  sentCount: number;
  lastSentAt: string;
  createdAt: string;
  createdBy: string;
  acceptedAt: string | null;
  workshopSlug: string | null;
  revokedAt: string | null;
  revokedBy: string | null;
  /** Pending and within 2 days of expiring (the owner gets one reminder then). */
  expiringSoon: boolean;
  remindedAt: string | null;
}

/** A workshop as the console lists it. */
export interface ConsoleWorkshop {
  id: string;
  slug: string;
  name: string;
  status: 'ACTIVE' | 'SUSPENDED';
  /** Made from the console for internal testing. */
  test: boolean;
  createdAt: string;
  staff: number;
  customers: number;
  locations: number;
  ownerUsername: string | null;
  ownerEmail: string | null;
  /** The setup invite it came from (null for test and older workshops). */
  invitedEmail: string | null;
  version: number;
}

export interface TestWorkshopRequest {
  workshopName: string;
  slug: string;
  currency: string;
  timeZone: string;
  adminUsername: string;
  adminEmail: string;
  adminPassword: string;
}

/** A new or resent invite: the setup link is shown this once (copy it when not e-mailed). */
export interface IssuedInvitation {
  invitation: ConsoleInvitation;
  setupUrl: string;
  emailed: boolean;
}

/** "Request a demo" on the public site. */
export type ShopType =
  'GENERAL_REPAIR' | 'TIRES' | 'QUICK_LUBE' | 'COLLISION' | 'DEALERSHIP' | 'FLEET' | 'OTHER';
export type LocationCount = '1' | '2-3' | '4-10' | '11+';

export interface DemoRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  shopName: string;
  locations: LocationCount;
  city: string | null;
  message: string | null;
}

export interface SignupRequest {
  workshopName: string;
  slug: string;
  labourRate?: number;
  currency?: string;
  timeZone?: string;
  adminUsername: string;
  adminEmail: string;
  adminPassword: string;
  /** From the setup link (<site>/setup?invite=…); sign-up is by invite only. */
  inviteToken: string;
  /** What else the owner does besides running it: the front desk and/or working on cars. */
  ownerAlso?: Role[];
}

/** Who a setup link is for (POST /api/v1/tenants/invitations/check). */
export interface InviteDetails {
  email: string;
  contactName: string | null;
  shopName: string | null;
  expiresAt: string;
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
  /** Google review requests. Missing from older servers (off). */
  reviews?: LocationReviews;
}

/** When a review request goes out after the invoice is paid. */
export type ReviewDelay = 'TWO_HOURS' | 'NEXT_MORNING' | 'TWO_DAYS';

/** A location's Google review requests: whether to ask, its review link (https), and when. */
export interface LocationReviews {
  enabled: boolean;
  url: string | null;
  delay: ReviewDelay;
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
  | 'INVOICE_ISSUED'
  | 'SERVICE_DUE';

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
// Hand-written; schema-drift.ts checks them against the API. Money is in the workshop's currency;
// ratios are 0–1.

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
  /** Invoiced fees and discounts: sales = parts + labour + fees − discounts (before tax). */
  feesSales: number;
  discounts: number;
  /** Payments received in the period, net of refunds. */
  collected: number;
}

export interface OpenWork {
  openCards: number;
  awaitingApproval: number;
  inWork: number;
  pendingValue: number;
  /** Issued invoices not fully paid, what they owe, and how much of it is past due. */
  unpaidInvoices: number;
  unpaidBalance: number;
  overdueBalance: number;
}

export interface PaymentRow {
  id: string;
  invoiceId: string;
  invoiceNumber: string;
  locationId: string;
  customerName: string;
  registration: string;
  method: PaymentMethod;
  /** Negative for a refund. */
  amount: number;
  reference: string | null;
  receivedAt: string;
  receivedBy: string;
}

export interface PaymentsReport {
  from: string;
  to: string;
  rows: PaymentRow[];
  truncated: boolean;
  byMethod: { method: PaymentMethod; count: number; amount: number }[];
  total: number;
}

export interface UnpaidRow {
  invoiceId: string;
  number: string;
  locationId: string;
  customerName: string;
  customerEmail: string | null;
  customerPhone: string | null;
  registration: string;
  issuedAt: string;
  dueAt: string;
  total: number;
  balanceDue: number;
  /** Days since it was issued. */
  days: number;
  overdue: boolean;
}

export interface UnpaidReport {
  asOf: string;
  rows: UnpaidRow[];
  truncated: boolean;
  /** 0–30, 31–60, 61–90 and over 90 days since issue. */
  buckets: { label: string; count: number; balance: number }[];
  totalBalance: number;
  overdueBalance: number;
}

export interface TaxReport {
  from: string;
  to: string;
  rows: { category: TaxCategory; rate: number; invoices: number; taxableAmount: number; tax: number }[];
  totalTax: number;
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
// Hand-written; schema-drift.ts checks them against the API. Money is in the invoice's currency.

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
  /** Where it stands in QuickBooks Online (staff only). */
  quickbooks: QuickBooksState | null;
  /** The last time it was e-mailed to the customer, and to which address (staff only). */
  emailedAt: string | null;
  emailedTo: string | null;
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

/** QuickBooks Online: NOT_SENT, SENT, OUT_OF_DATE (payments or the void not sent yet) or FAILED. */
export type QuickBooksSyncStatus = 'NOT_SENT' | 'SENT' | 'OUT_OF_DATE' | 'FAILED';

export interface QuickBooksState {
  status: QuickBooksSyncStatus;
  invoiceId: string | null;
  syncedAt: string | null;
  error: string | null;
  paymentsToSend: number;
  refundsByHand: number;
}

export interface QuickBooksMapping {
  partsItemId: string;
  partsItemName: string | null;
  labourItemId: string;
  labourItemName: string | null;
  feesItemId: string;
  feesItemName: string | null;
  depositAccountId: string | null;
  depositAccountName: string | null;
}

/** available: the server has Intuit keys; ready: connected with parts, labour and fees mapped. */
export interface QuickBooksStatus {
  available: boolean;
  connected: boolean;
  ready: boolean;
  environment: 'sandbox' | 'production' | null;
  companyName: string | null;
  country: string | null;
  connectedAt: string | null;
  connectedBy: string | null;
  mapping: QuickBooksMapping | null;
}

/** A product/service or an account in QuickBooks. */
export interface QuickBooksChoice {
  id: string;
  name: string;
  type: string;
}

/** Exports for the accountant: issued invoices with their lines, and payments, of a period. */
export interface ExportLine {
  type: InvoiceLineType;
  description: string;
  partNumber: string | null;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  taxable: boolean;
  taxCategory: TaxCategory;
}

export interface ExportInvoice {
  id: string;
  number: string;
  status: InvoiceStatus;
  locationId: string;
  customerName: string;
  customerEmail: string | null;
  customerPhone: string | null;
  registration: string;
  vin: string;
  vehicleDescription: string | null;
  currency: string;
  issuedAt: string;
  dueAt: string | null;
  paymentTermsDays: number;
  lines: ExportLine[];
  subtotal: number;
  taxTotal: number;
  total: number;
  amountPaid: number;
  balanceDue: number;
}

export interface ExportPayment {
  id: string;
  invoiceId: string;
  invoiceNumber: string;
  locationId: string;
  customerName: string;
  currency: string;
  method: PaymentMethod;
  amount: number;
  reference: string | null;
  receivedAt: string;
  receivedBy: string;
}

export interface InvoiceExport {
  from: string;
  to: string;
  invoices: ExportInvoice[];
  payments: ExportPayment[];
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

/** Admin > Activity: what was done (the schema says string). */
export type AuditAction =
  | 'STAFF_CREATED'
  | 'ROLES_CHANGED'
  | 'LOCATIONS_CHANGED'
  | 'ACCOUNT_ENABLED'
  | 'ACCOUNT_DISABLED'
  | 'ACCOUNT_UNLOCKED'
  | 'PASSWORD_RESET'
  | 'AVATAR_RESET';

/** One change to an account. Names are as they were at the time; null when not known. */
export interface AuditEvent {
  id: string;
  occurredAt: string;
  action: AuditAction;
  actorId?: string | null;
  actorName?: string | null;
  targetId?: string | null;
  targetName?: string | null;
  details?: string | null;
  requestId?: string | null;
}

// ---- estimate approval links ------------------------------------------------------------
// Hand-written until `pnpm api:update` (then add them to schema-drift.ts).

/** The approval link e-mailed for a job card's estimate, as staff see it. */
export interface EstimateLinkStatus {
  sentTo: string;
  sentBy: string;
  /** The first e-mail; `lastSentAt` the latest reminder. */
  sentAt: string;
  lastSentAt: string;
  expiresAt: string;
  firstViewedAt: string | null;
  remindersSent: number;
  nextReminderAt: string | null;
  decidedAt: string | null;
  signerName: string | null;
  signed: boolean;
  /** The customer can still answer from the link. */
  open: boolean;
}

export interface CustomerEstimateItem {
  id: string;
  description: string;
  /** Parts plus labour, in the estimate's currency. */
  price: number;
  status: ApprovalStatus;
}

export interface CustomerEstimateFinding {
  description: string;
  severity: Severity;
}

/** What the customer sees from their link (no sign-in). */
export interface CustomerEstimate {
  shopName: string | null;
  locationName: string | null;
  shopPhone: string | null;
  firstName: string | null;
  vehicle: string;
  status: JobCardStatus;
  /** They can still approve or decline here. */
  canDecide: boolean;
  findings: CustomerEstimateFinding[];
  items: CustomerEstimateItem[];
  total: number;
  currency: string;
  expiresAt: string;
  decidedAt: string | null;
  signerName: string | null;
}

export interface EstimateDecisionRequest {
  token: string;
  decisions: { itemId: string; approve: boolean }[];
  signerName: string;
  /** The drawn signature, as a PNG data URL. */
  signature: string;
}

// ---- VIN decode and quick estimates -------------------------------------------------------

/** A VIN decoded by NHTSA vPIC. `warnings`: e.g. a check digit that doesn't match. */
export interface DecodedVin {
  vin: string;
  year: number | null;
  make: string | null;
  model: string | null;
  trim: string | null;
  engine: string | null;
  bodyClass: string | null;
  warnings: string[];
}

/** A quick estimate: the car (with VIN and plate) and what the customer asked for. */
export type CreateEstimateRequest = P['CreateEstimateRequest'];
export type ScheduleEstimateRequest = P['ScheduleEstimateRequest'];

/** What the public booking page fills in from a VIN. */
export type PublicVin = P['PublicVin'];

// ---- Google review requests (workshop-service) ----------------------------------------------

/** A paid invoice's review request. NONE: nothing scheduled (requests off at the location). */
export interface ReviewRequestStatus {
  status: 'NONE' | 'SCHEDULED' | 'SENT' | 'SKIPPED' | 'CANCELLED';
  dueAt: string | null;
  sentAt: string | null;
  sentTo: string | null;
  /** Who sent it ("automatic" for scheduled ones). */
  sentBy: string | null;
  /** Why it was skipped or cancelled. */
  reason: string | null;
}
