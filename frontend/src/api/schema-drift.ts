/**
 * Keeps the hand-written API types in ./types.ts honest: each must only have fields the API's
 * own (generated) type has. When the backend renames or drops a field, `pnpm typecheck` fails
 * here, naming the field, instead of the app breaking at run time. Nothing imports this file;
 * it only has to type-check.
 */
import type { components as AppointmentApi } from './schema/appointment';
import type { components as AuthApi } from './schema/auth';
import type { components as WorkshopApi } from './schema/workshop';
import type * as T from './types';

type A = AuthApi['schemas'];
type P = AppointmentApi['schemas'];
type W = WorkshopApi['schemas'];

/** The hand-written type's fields that the API's own type does not have (never = none). */
type Extra<Hand, Generated> = Exclude<keyof Hand, keyof Generated>;
type None<K extends never> = K;

export type DriftGuard = [
  None<Extra<T.CustomerAccount, A['CustomerAccount']>>,
  None<Extra<T.Avatar, A['AvatarResponse']>>,
  None<Extra<T.TemporaryPassword, A['TemporaryPasswordResponse']>>,
  None<Extra<T.ForgotPasswordRequest, A['ForgotPasswordRequest']>>,
  None<Extra<T.ResetPasswordRequest, A['ResetPasswordRequest']>>,
  None<Extra<T.ChangePasswordRequest, A['ChangePasswordRequest']>>,
  None<Extra<T.CannedJob, W['CannedJobResponse']>>,
  None<Extra<T.CannedJobRequest, W['CannedJobRequest']>>,
  None<Extra<T.DueService, W['DueService']>>,
  None<Extra<T.DueVehicle, W['DueVehicle']>>,
  None<Extra<T.WorkRequest, W['WorkRequest']>>,
  None<Extra<T.Workshop, A['WorkshopResponse']>>,
  None<Extra<T.PublicWorkshop, A['PublicWorkshop']>>,
  None<Extra<T.PublicAnnouncement, A['PublicAnnouncement']>>,
  None<Extra<T.PublicBranding, A['PublicBranding']>>,
  None<Extra<T.Branding, A['BrandingResponse']>>,
  None<Extra<T.UpdateBrandingRequest, A['UpdateBrandingRequest']>>,
  None<Extra<T.Announcement, A['AnnouncementResponse']>>,
  None<Extra<T.AnnouncementRequest, A['AnnouncementRequest']>>,
  None<Extra<T.BookingServiceRequest, P['BookingServiceRequest']>>,
  None<Extra<T.ManagedBooking, P['ManagedBooking']>>,
  None<Extra<T.TestWorkshopRequest, A['TestWorkshopRequest']>>,
  None<Extra<T.DemoRequest, A['DemoRequestResponse']>>,
  None<Extra<T.SignupRequest, A['SignupRequest']>>,
  None<Extra<T.InviteDetails, A['InviteDetails']>>,
  None<Extra<T.SignupResponse, A['SignupResponse']>>,
  None<Extra<T.Location, A['LocationResponse']>>,
  None<Extra<T.LocationRequest, A['LocationRequest']>>,
  None<Extra<T.PeriodMetrics, W['PeriodMetrics']>>,
  None<Extra<T.OpenWork, W['OpenWork']>>,
  None<Extra<T.PaymentRow, W['PaymentRow']>>,
  None<Extra<T.UnpaidRow, W['UnpaidRow']>>,
  None<Extra<T.TechnicianRow, W['TechnicianRow']>>,
  None<Extra<T.AdvisorRow, W['AdvisorRow']>>,
  None<Extra<T.DeclinedRow, W['DeclinedRow']>>,
  None<Extra<T.Invoice, W['InvoiceResponse']>>,
  None<Extra<T.InvoiceSummary, W['InvoiceSummary']>>,
  None<Extra<T.QuickBooksState, W['QuickBooksState']>>,
  None<Extra<T.ExportLine, W['ExportLine']>>,
  None<Extra<T.ExportInvoice, W['ExportInvoice']>>,
  None<Extra<T.ExportPayment, W['ExportPayment']>>,
  None<Extra<T.InvoiceExport, W['InvoiceExport']>>,
  None<Extra<T.PaymentRequest, W['PaymentRequest']>>,
  None<Extra<T.AuditEvent, A['AuditEventResponse']>>,
  None<Extra<T.EstimateLinkStatus, W['EstimateLinkStatus']>>,
  None<Extra<T.CustomerEstimate, W['CustomerEstimate']>>,
  None<Extra<T.CustomerEstimateItem, W['CustomerEstimateItem']>>,
  None<Extra<T.CustomerEstimateFinding, W['CustomerEstimateFinding']>>,
  None<Extra<T.EstimateDecisionRequest, W['EstimateDecisionRequest']>>,
  None<Extra<T.DecodedVin, P['DecodedVin']>>,
  None<Extra<T.JobCard, W['JobCardResponse']>>,
  None<Extra<T.JobCardSummary, W['JobCardSummary']>>,
];
