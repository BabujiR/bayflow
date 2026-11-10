import type { AppointmentStatus, ApprovalStatus, JobCardStatus, Severity } from '@/api/types';
import { Badge } from '@/components/ui/badge';
import { APPOINTMENT_STATUS_LABELS, JOB_CARD_STATUS_LABELS, SEVERITY_LABELS } from '@/lib/format';

type Variant = 'default' | 'secondary' | 'success' | 'warning' | 'destructive' | 'outline';

const APPOINTMENT_VARIANTS: Record<AppointmentStatus, Variant> = {
  BOOKED: 'secondary',
  CONFIRMED: 'default',
  ARRIVED: 'warning',
  CHECKED_IN: 'success',
  CANCELLED: 'outline',
  NO_SHOW: 'destructive',
};

const JOB_CARD_VARIANTS: Record<JobCardStatus, Variant> = {
  OPEN: 'secondary',
  INSPECTED: 'secondary',
  AWAITING_APPROVAL: 'warning',
  APPROVED: 'default',
  PARTIALLY_APPROVED: 'default',
  DECLINED: 'outline',
  IN_PROGRESS: 'warning',
  COMPLETED: 'success',
};

export function AppointmentStatusBadge({ status }: { status: AppointmentStatus }) {
  return <Badge variant={APPOINTMENT_VARIANTS[status]}>{APPOINTMENT_STATUS_LABELS[status]}</Badge>;
}

export function JobCardStatusBadge({ status }: { status: JobCardStatus }) {
  return <Badge variant={JOB_CARD_VARIANTS[status]}>{JOB_CARD_STATUS_LABELS[status]}</Badge>;
}

export function ApprovalBadge({ status }: { status: ApprovalStatus }) {
  const variant: Variant = status === 'APPROVED' ? 'success' : status === 'DECLINED' ? 'outline' : 'warning';
  const label = status === 'PENDING' ? 'Waiting for decision' : status === 'APPROVED' ? 'Approved' : 'Declined';
  return <Badge variant={variant}>{label}</Badge>;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  const variant: Variant =
    severity === 'CRITICAL' ? 'destructive' : severity === 'HIGH' ? 'warning' : severity === 'MEDIUM' ? 'default' : 'secondary';
  return <Badge variant={variant}>{SEVERITY_LABELS[severity]}</Badge>;
}
