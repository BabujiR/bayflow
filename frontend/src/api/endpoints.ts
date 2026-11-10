import { http } from './http';
import type {
  Appointment,
  AppointmentStatus,
  BookAppointmentRequest,
  CreateCustomerRequest,
  CreateStaffRequest,
  CreateVehicleRequest,
  Customer,
  FindingRequest,
  JobCard,
  JobCardStatus,
  JobCardSummary,
  LabourRequest,
  PageResponse,
  PartRequest,
  ProfileRequest,
  RecommendationRequest,
  RescheduleRequest,
  ServiceHistoryEntry,
  UpdateVehicleRequest,
  User,
  Vehicle,
  WorkLogRequest,
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
  list: (params: Paging & { q?: string }) => http.get<PageResponse<Customer>>('/api/v1/customers', { ...params }),
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
}

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
  byAppointment: (appointmentId: string) => http.get<JobCard>(`/api/v1/job-cards/by-appointment/${appointmentId}`),
  addFinding: (id: string, body: FindingRequest) => http.post<JobCard>(`/api/v1/job-cards/${id}/findings`, body),
  addRecommendation: (id: string, body: RecommendationRequest) =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/recommendations`, body),
  submit: (id: string) => http.post<JobCard>(`/api/v1/job-cards/${id}/submit-for-approval`),
  decide: (id: string, itemId: string, decision: 'approve' | 'decline') =>
    http.post<JobCard>(`/api/v1/job-cards/${id}/recommendations/${itemId}/${decision}`),
  start: (id: string) => http.post<JobCard>(`/api/v1/job-cards/${id}/start`),
  logWork: (id: string, body: WorkLogRequest) => http.post<JobCard>(`/api/v1/job-cards/${id}/work-logs`, body),
  addPart: (id: string, body: PartRequest) => http.post<JobCard>(`/api/v1/job-cards/${id}/parts`, body),
  addLabour: (id: string, body: LabourRequest) => http.post<JobCard>(`/api/v1/job-cards/${id}/labour`, body),
  complete: (id: string) => http.post<JobCard>(`/api/v1/job-cards/${id}/complete`),
};

export const historyApi = {
  forVehicle: (vehicleId: string, params: Paging = {}) =>
    http.get<PageResponse<ServiceHistoryEntry>>(`/api/v1/service-history/vehicles/${vehicleId}`, { ...params }),
};

// ---- auth-service ----------------------------------------------------------------------------

export const usersApi = {
  list: (params: Paging & { sort?: string }) => http.get<PageResponse<User>>('/api/v1/auth/users', { ...params }),
  create: (body: CreateStaffRequest) => http.post<User>('/api/v1/auth/users', body),
  setEnabled: (id: string, enabled: boolean) => http.patch<User>(`/api/v1/auth/users/${id}`, { enabled }),
};
