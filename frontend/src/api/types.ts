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

export type Role = A['CreateStaffRequest']['role'];

export type User = Present<Omit<A['UserResponse'], 'roles'>, 'id' | 'username' | 'email' | 'enabled' | 'createdAt'> & {
  roles: Role[];
};

export type TokenResponse = Present<
  Omit<A['TokenResponse'], 'user'>,
  'accessToken' | 'tokenType' | 'expiresIn' | 'expiresAt'
> & { user: User };

export type LoginRequest = A['LoginRequest'];
export type RegisterRequest = A['RegisterRequest'];
export type CreateStaffRequest = A['CreateStaffRequest'];

// ---- appointments, customers, vehicles ------------------------------------------------

export type ServiceType = P['BookAppointmentRequest']['serviceType'];
export type AppointmentStatus = NonNullable<P['AppointmentResponse']['status']>;

export type Customer = Present<P['CustomerResponse'], 'id' | 'name' | 'email' | 'createdAt'>;

export type Vehicle = Present<
  P['VehicleResponse'],
  'id' | 'customerId' | 'vin' | 'registration' | 'make' | 'model' | 'year' | 'mileage' | 'createdAt'
>;

export type VehicleSummary = Present<P['VehicleSummary'], 'id' | 'vin' | 'registration' | 'make' | 'model' | 'year'>;

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
> & { vehicle: VehicleSummary };

export type ProfileRequest = P['ProfileRequest'];
export type CreateCustomerRequest = P['CreateCustomerRequest'];
export type CreateVehicleRequest = P['CreateVehicleRequest'];
export type UpdateVehicleRequest = P['UpdateVehicleRequest'];
export type BookAppointmentRequest = P['BookAppointmentRequest'];
export type RescheduleRequest = P['RescheduleRequest'];

// ---- workshop -----------------------------------------------------------------------

export type JobCardStatus = NonNullable<W['JobCardResponse']['status']>;
export type Severity = W['FindingRequest']['severity'];
export type ApprovalStatus = NonNullable<W['RecommendationResponse']['approvalStatus']>;

export type Finding = Present<W['FindingResponse'], 'id' | 'description' | 'severity' | 'recordedBy' | 'createdAt'>;

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

export type WorkLog = Present<W['WorkLogResponse'], 'id' | 'recommendationId' | 'mechanic' | 'description' | 'completedAt'>;

export type Part = Present<
  W['PartResponse'],
  'id' | 'recommendationId' | 'partNumber' | 'name' | 'quantity' | 'unitPrice' | 'lineTotal' | 'recordedBy'
>;

export type Labour = Present<W['LabourResponse'], 'id' | 'recommendationId' | 'mechanic' | 'hours' | 'hourlyRate' | 'cost'>;

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
};

export type JobCardSummary = Present<
  W['JobCardSummary'],
  'id' | 'appointmentId' | 'vehicleId' | 'vin' | 'registration' | 'customerName' | 'mileage' | 'status' | 'createdAt' | 'updatedAt'
>;

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
