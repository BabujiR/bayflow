import { Link } from 'react-router';

import type { LocationSalesRow, TechnicianRow } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useLocationLookup } from '@/features/locations/use-locations';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { formatMoney } from '@/lib/format';

import { describeChange, describePointChange, formatCount, formatHours, formatPercent } from './format';
import { KpiTile } from './KpiTile';
import { daysBetween, PERIOD_LABELS, type Period } from './period';
import { PeriodPicker } from './PeriodPicker';
import { SalesTrend } from './SalesTrend';
import {
  useDailySales,
  useLocationSales,
  usePeriodParam,
  useReportSummary,
  useTechnicianReport,
} from './use-reports';

/**
 * The owner's dashboard (ADMIN, MANAGER; their home page): sales, car count, average repair
 * order and close ratio for the period against the one before, today's open work, technicians
 * and locations. Refreshes every minute while open.
 */
export function DashboardPage() {
  const workshop = useWorkshop();
  if (!workshop.data) {
    return (
      <div aria-busy="true" aria-label="Loading" className="grid gap-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-28" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  return <Dashboard zone={workshop.data.timeZone} currency={workshop.data.currency} />;
}

export function periodDescription(period: Period): string {
  const days = daysBetween(period.firstDay, period.lastDay);
  const span =
    period.firstDay === period.lastDay ? period.firstDay : `${period.firstDay} to ${period.lastDay}`;
  const label = period.preset === 'custom' ? '' : `${PERIOD_LABELS[period.preset]} · `;
  return `${label}${span} · compared with the ${days === 1 ? 'day' : `${days} days`} before`;
}

function Dashboard({ zone, currency }: { zone?: string; currency: string }) {
  const [period, setPeriod] = usePeriodParam(zone, 'month');
  const summary = useReportSummary(period, true);
  const daily = useDailySales(period, zone, true);
  const technicians = useTechnicianReport(period, true);
  const locations = useLocationSales(period, true);
  const { hasSeveral } = useLocationLookup();

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={periodDescription(period)}
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <PeriodPicker key={`${period.preset}-${period.firstDay}`} period={period} onChange={setPeriod} />
            <Button asChild variant="outline" size="sm" className="h-9">
              <Link to={`/reports?${new URLSearchParams(periodParams(period))}`}>Reports</Link>
            </Button>
          </div>
        }
      />
      <div className="grid gap-6">
        <QueryState query={summary} skeleton={<Skeleton className="h-56" />}>
          {({ current, previous, open }) => (
            <>
              <section
                aria-label="This period"
                className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6"
              >
                <KpiTile
                  label="Sales"
                  value={formatMoney(current.sales, currency)}
                  changeText={describeChange(current.sales, previous.sales)}
                  hint={`was ${formatMoney(previous.sales, currency)}`}
                />
                <KpiTile
                  label="Car count"
                  value={formatCount(current.carCount)}
                  changeText={describeChange(current.carCount, previous.carCount)}
                  hint={`was ${formatCount(previous.carCount)}`}
                />
                <KpiTile
                  label="Average repair order"
                  value={formatMoney(current.averageRepairOrder, currency)}
                  changeText={describeChange(current.averageRepairOrder, previous.averageRepairOrder)}
                  hint={`was ${formatMoney(previous.averageRepairOrder, currency)}`}
                />
                <KpiTile
                  label="Close ratio"
                  value={formatPercent(current.closeRatio)}
                  changeText={describePointChange(current.closeRatio, previous.closeRatio)}
                  hint={`${formatMoney(current.approvedValue, currency)} of ${formatMoney(
                    current.approvedValue + current.declinedValue,
                    currency,
                  )} approved`}
                />
                <KpiTile
                  label="Hours billed"
                  value={formatHours(current.labourHours)}
                  changeText={describeChange(current.labourHours, previous.labourHours)}
                  hint={`was ${formatHours(previous.labourHours)}`}
                />
                <KpiTile
                  label="Effective labour rate"
                  value={formatMoney(current.effectiveLabourRate, currency)}
                  changeText={describeChange(current.effectiveLabourRate, previous.effectiveLabourRate)}
                  hint="per hour billed"
                />
              </section>
              <section aria-label="In the shop now" className="grid gap-3 sm:grid-cols-3">
                <KpiTile
                  label="Open job cards"
                  value={formatCount(open.openCards)}
                  hint={
                    <Link to="/job-cards" className="text-primary hover:underline">
                      View job cards
                    </Link>
                  }
                />
                <KpiTile
                  label="Waiting for approval"
                  value={formatCount(open.awaitingApproval)}
                  hint={`${formatMoney(open.pendingValue, currency)} estimated`}
                />
                <KpiTile label="Approved or in work" value={formatCount(open.inWork)} />
              </section>
            </>
          )}
        </QueryState>

        <Card>
          <CardHeader>
            <CardTitle>Sales</CardTitle>
            <CardDescription>Completed jobs, by the day they were completed.</CardDescription>
          </CardHeader>
          <CardContent>
            <QueryState query={daily} skeleton={<Skeleton className="h-48" />}>
              {(report) => (
                <SalesTrend
                  days={report.days}
                  firstDay={period.firstDay}
                  lastDay={period.lastDay}
                  currency={currency}
                />
              )}
            </QueryState>
          </CardContent>
        </Card>

        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Technicians</CardTitle>
              <CardDescription>Hours logged in the period, on finished and unfinished jobs.</CardDescription>
            </CardHeader>
            <CardContent>
              <QueryState query={technicians}>
                {(rows) => <TechnicianTable rows={rows.slice(0, 8)} currency={currency} />}
              </QueryState>
            </CardContent>
          </Card>
          {hasSeveral && (
            <Card>
              <CardHeader>
                <CardTitle>Locations</CardTitle>
                <CardDescription>Completed sales per location.</CardDescription>
              </CardHeader>
              <CardContent>
                <QueryState query={locations}>
                  {(rows) => <LocationTable rows={rows} currency={currency} />}
                </QueryState>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

/** The period as address parameters, to open the same period on another page. */
export function periodParams(period: Period): Record<string, string> {
  return period.preset === 'custom'
    ? { from: period.firstDay, to: period.lastDay }
    : { period: period.preset };
}

export function TechnicianTable({ rows, currency }: { rows: TechnicianRow[]; currency: string }) {
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground">No hours logged in this period.</p>;
  }
  return (
    <Table aria-label="Technicians">
      <TableHeader>
        <TableRow>
          <TableHead>Mechanic</TableHead>
          <TableHead className="text-right">Hours</TableHead>
          <TableHead className="text-right">Labour value</TableHead>
          <TableHead className="text-right">Job cards</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.mechanic}>
            <TableCell className="font-medium">{r.mechanic}</TableCell>
            <TableCell className="text-right tabular-nums">{formatHours(r.hours)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatMoney(r.labourValue, currency)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatCount(r.jobCards)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function LocationTable({ rows, currency }: { rows: LocationSalesRow[]; currency: string }) {
  const { nameOf } = useLocationLookup();
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground">No completed jobs in this period.</p>;
  }
  return (
    <Table aria-label="Locations">
      <TableHeader>
        <TableRow>
          <TableHead>Location</TableHead>
          <TableHead className="text-right">Cars</TableHead>
          <TableHead className="text-right">Sales</TableHead>
          <TableHead className="text-right">ARO</TableHead>
          <TableHead className="text-right">Hours</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.locationId}>
            <TableCell className="font-medium">{nameOf(r.locationId)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatCount(r.carCount)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatMoney(r.sales, currency)}</TableCell>
            <TableCell className="text-right tabular-nums">
              {formatMoney(r.averageRepairOrder, currency)}
            </TableCell>
            <TableCell className="text-right tabular-nums">{formatHours(r.labourHours)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
