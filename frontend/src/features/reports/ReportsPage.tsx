import type { UseQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';

import type {
  AdvisorRow,
  DailySales,
  DeclinedRow,
  LocationSalesRow,
  PartUsageRow,
  PeriodMetrics,
  ReportRows,
  TechnicianRow,
} from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { QueryState } from '@/components/common/QueryState';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useLocationLookup } from '@/features/locations/use-locations';
import { useWorkshop } from '@/features/workshop/use-workshop';
import { cn } from '@/lib/utils';
import { formatDateTime, formatMoney } from '@/lib/format';

import { type CsvColumn, csvFilename, downloadCsv, toCsv } from './csv';
import { periodDescription, periodParams } from './DashboardPage';
import { describeChange, describePointChange, formatCount, formatHours, formatPercent } from './format';
import { eachDay, type Period } from './period';
import { PeriodPicker } from './PeriodPicker';
import {
  useAdvisorReport,
  useDailySales,
  useDeclinedReport,
  useLocationSales,
  usePartsReport,
  usePeriodParam,
  useReportSummary,
  useTechnicianReport,
} from './use-reports';

export const REPORT_TABS = {
  summary: 'Summary',
  sales: 'Sales details',
  advisors: 'Service advisors',
  technicians: 'Technicians',
  declined: 'Declined jobs',
  parts: 'Parts usage',
} as const;

type Tab = keyof typeof REPORT_TABS;
const TABS = Object.keys(REPORT_TABS) as Tab[];

/**
 * Reports (ADMIN, MANAGER), each for a period and the locations in view, each exportable as
 * CSV. The tab and period are in the address, so a report can be bookmarked.
 */
export function ReportsPage() {
  const workshop = useWorkshop();
  if (!workshop.data) {
    return (
      <div aria-busy="true" aria-label="Loading" className="grid gap-4">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  return <Reports zone={workshop.data.timeZone} currency={workshop.data.currency} />;
}

function Reports({ zone, currency }: { zone?: string; currency: string }) {
  const [period, setPeriod] = usePeriodParam(zone, 'month');
  const [params, setParams] = useSearchParams();
  const tab: Tab = TABS.includes(params.get('tab') as Tab) ? (params.get('tab') as Tab) : 'summary';

  const selectTab = (next: Tab) =>
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        updated.set('tab', next);
        return updated;
      },
      { replace: true },
    );

  return (
    <>
      <PageHeader
        title="Reports"
        description={periodDescription(period)}
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <PeriodPicker key={`${period.preset}-${period.firstDay}`} period={period} onChange={setPeriod} />
            <Button asChild variant="outline" size="sm" className="h-9">
              <Link to={`/dashboard?${new URLSearchParams(periodParams(period))}`}>Dashboard</Link>
            </Button>
          </div>
        }
      />
      <div role="tablist" aria-label="Reports" className="mb-6 flex flex-wrap gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            id={`report-tab-${t}`}
            aria-selected={tab === t}
            aria-controls="report-panel"
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              tab === t
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
            onClick={() => selectTab(t)}
          >
            {REPORT_TABS[t]}
          </button>
        ))}
      </div>
      <div role="tabpanel" id="report-panel" aria-labelledby={`report-tab-${tab}`}>
        {tab === 'summary' && <SummaryReport period={period} currency={currency} />}
        {tab === 'sales' && <SalesReport period={period} zone={zone} currency={currency} />}
        {tab === 'advisors' && <AdvisorReport period={period} currency={currency} />}
        {tab === 'technicians' && <TechnicianReport period={period} currency={currency} />}
        {tab === 'declined' && <DeclinedReport period={period} currency={currency} />}
        {tab === 'parts' && <PartsReport period={period} currency={currency} />}
      </div>
    </>
  );
}

// ---- building blocks ----------------------------------------------------------------------

/** A report column: shown in the table and exported to CSV (raw numbers, no currency symbols). */
interface Column<T> extends CsvColumn<T> {
  cell: (row: T) => ReactNode;
  numeric?: boolean;
}

function ReportTable<T>({
  name,
  label,
  period,
  columns,
  rows,
  rowKey,
  empty,
  footer,
}: {
  name: string;
  label: string;
  period: Period;
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  empty: string;
  footer?: ReactNode;
}) {
  return (
    <section aria-label={label} className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">{label}</h2>
        <Button
          variant="outline"
          size="sm"
          disabled={rows.length === 0}
          onClick={() => downloadCsv(csvFilename(name, period.firstDay, period.lastDay), toCsv(columns, rows))}
        >
          Export CSV
        </Button>
      </div>
      {rows.length === 0 ? (
        <p className="rounded-md border border-dashed p-6 text-center text-sm text-muted-foreground">{empty}</p>
      ) : (
        <div className="overflow-x-auto">
          <Table aria-label={label}>
            <TableHeader>
              <TableRow>
                {columns.map((c) => (
                  <TableHead key={c.header} className={cn(c.numeric && 'text-right')}>
                    {c.header}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={rowKey(row)}>
                  {columns.map((c) => (
                    <TableCell key={c.header} className={cn(c.numeric && 'text-right tabular-nums')}>
                      {c.cell(row)}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {footer}
    </section>
  );
}

function Report<T>({ query, children }: { query: UseQueryResult<T>; children: (data: T) => ReactNode }) {
  return (
    <QueryState query={query} skeleton={<Skeleton className="h-64" />}>
      {children}
    </QueryState>
  );
}

function Truncated({ report }: { report: ReportRows<unknown> }) {
  return report.truncated ? (
    <Alert>Showing the latest 1,000 rows. Choose a shorter period to see them all.</Alert>
  ) : null;
}

const money = (currency: string) => (amount: number) => formatMoney(amount, currency);

// ---- Summary ------------------------------------------------------------------------------

interface SummaryLine {
  measure: string;
  current: number | null;
  previous: number | null;
  show: (value: number | null) => string;
  points?: boolean;
}

function SummaryReport({ period, currency }: { period: Period; currency: string }) {
  const summary = useReportSummary(period);
  return (
    <Report query={summary}>
      {({ current, previous, previousFrom, open }) => {
        const m = money(currency);
        const amount = (v: number | null) => (v === null ? '—' : m(v));
        const count = (v: number | null) => (v === null ? '—' : formatCount(v));
        const line = (
          measure: string,
          pick: (p: PeriodMetrics) => number | null,
          show: (v: number | null) => string,
          points = false,
        ): SummaryLine => ({ measure, current: pick(current), previous: pick(previous), show, points });
        const lines: SummaryLine[] = [
          line('Car count (completed jobs)', (p) => p.carCount, count),
          line('Sales', (p) => p.sales, amount),
          line('Parts sales', (p) => p.partsSales, amount),
          line('Labour sales', (p) => p.labourSales, amount),
          line('Average repair order', (p) => p.averageRepairOrder, amount),
          line('Hours billed', (p) => p.labourHours, formatHours),
          line('Effective labour rate', (p) => p.effectiveLabourRate, amount),
          line('Work approved', (p) => p.approvedValue, amount),
          line('Work declined', (p) => p.declinedValue, amount),
          line('Hours approved', (p) => p.hoursApproved, formatHours),
          line('Hours declined', (p) => p.hoursDeclined, formatHours),
          line('Close ratio', (p) => p.closeRatio, (v) => formatPercent(v, 1), true),
        ];
        const columns: Column<SummaryLine>[] = [
          { header: 'Measure', value: (l) => l.measure, cell: (l) => <span className="font-medium">{l.measure}</span> },
          { header: 'This period', value: (l) => l.current, cell: (l) => l.show(l.current), numeric: true },
          {
            header: `Previous (from ${previousFrom.slice(0, 10)})`,
            value: (l) => l.previous,
            cell: (l) => l.show(l.previous),
            numeric: true,
          },
          {
            header: 'Change',
            value: (l) =>
              l.points ? describePointChange(l.current, l.previous) : describeChange(l.current, l.previous),
            cell: (l) =>
              l.points ? describePointChange(l.current, l.previous) : describeChange(l.current, l.previous),
            numeric: true,
          },
        ];
        return (
          <div className="grid gap-6">
            <ReportTable
              name="summary"
              label="Shop summary"
              period={period}
              columns={columns}
              rows={lines}
              rowKey={(l) => l.measure}
              empty=""
            />
            <p className="text-sm text-muted-foreground">
              In the shop now: {formatCount(open.openCards)} open job cards, {formatCount(open.awaitingApproval)}{' '}
              waiting for approval ({m(open.pendingValue)}), {formatCount(open.inWork)} approved or in work.
            </p>
          </div>
        );
      }}
    </Report>
  );
}

// ---- Sales details ------------------------------------------------------------------------

function SalesReport({ period, zone, currency }: { period: Period; zone?: string; currency: string }) {
  const daily = useDailySales(period, zone);
  const locations = useLocationSales(period);
  const { nameOf, hasSeveral } = useLocationLookup();
  const m = money(currency);

  const dayColumns: Column<DailySales>[] = [
    { header: 'Day', value: (d) => d.day, cell: (d) => d.day },
    { header: 'Cars', value: (d) => d.carCount, cell: (d) => formatCount(d.carCount), numeric: true },
    { header: 'Parts', value: (d) => d.partsSales, cell: (d) => m(d.partsSales), numeric: true },
    { header: 'Labour', value: (d) => d.labourSales, cell: (d) => m(d.labourSales), numeric: true },
    { header: 'Sales', value: (d) => d.sales, cell: (d) => m(d.sales), numeric: true },
    {
      header: 'ARO',
      value: (d) => (d.carCount ? Math.round((d.sales / d.carCount) * 100) / 100 : 0),
      cell: (d) => m(d.carCount ? d.sales / d.carCount : 0),
      numeric: true,
    },
  ];
  const locationColumns: Column<LocationSalesRow>[] = [
    { header: 'Location', value: (r) => nameOf(r.locationId), cell: (r) => nameOf(r.locationId) },
    { header: 'Cars', value: (r) => r.carCount, cell: (r) => formatCount(r.carCount), numeric: true },
    { header: 'Parts', value: (r) => r.partsSales, cell: (r) => m(r.partsSales), numeric: true },
    { header: 'Labour', value: (r) => r.labourSales, cell: (r) => m(r.labourSales), numeric: true },
    { header: 'Sales', value: (r) => r.sales, cell: (r) => m(r.sales), numeric: true },
    { header: 'ARO', value: (r) => r.averageRepairOrder, cell: (r) => m(r.averageRepairOrder), numeric: true },
    { header: 'Hours billed', value: (r) => r.labourHours, cell: (r) => formatHours(r.labourHours), numeric: true },
  ];

  return (
    <div className="grid gap-8">
      <Report query={daily}>
        {(report) => {
          // Every day of the period, including days without sales.
          const byDay = new Map(report.days.map((d) => [d.day, d]));
          const rows = eachDay(period.firstDay, period.lastDay).map(
            (day) => byDay.get(day) ?? { day, carCount: 0, sales: 0, partsSales: 0, labourSales: 0 },
          );
          return (
            <ReportTable
              name="sales-by-day"
              label="Sales by day"
              period={period}
              columns={dayColumns}
              rows={rows}
              rowKey={(d) => d.day}
              empty="No days in this period."
            />
          );
        }}
      </Report>
      {hasSeveral && (
        <Report query={locations}>
          {(rows) => (
            <ReportTable
              name="sales-by-location"
              label="Sales by location"
              period={period}
              columns={locationColumns}
              rows={rows}
              rowKey={(r) => r.locationId}
              empty="No completed jobs in this period."
            />
          )}
        </Report>
      )}
    </div>
  );
}

// ---- Service advisors ---------------------------------------------------------------------

function AdvisorReport({ period, currency }: { period: Period; currency: string }) {
  const advisors = useAdvisorReport(period);
  const m = money(currency);
  const columns: Column<AdvisorRow>[] = [
    {
      header: 'Advisor',
      value: (r) => r.advisor || 'Not recorded',
      cell: (r) => (r.advisor ? <span className="font-medium">{r.advisor}</span> : 'Not recorded'),
    },
    { header: 'Estimates sent', value: (r) => r.estimatesSent, cell: (r) => formatCount(r.estimatesSent), numeric: true },
    { header: 'Presented', value: (r) => r.presentedValue, cell: (r) => m(r.presentedValue), numeric: true },
    { header: 'Approved', value: (r) => r.approvedValue, cell: (r) => m(r.approvedValue), numeric: true },
    { header: 'Declined', value: (r) => r.declinedValue, cell: (r) => m(r.declinedValue), numeric: true },
    {
      header: 'Close ratio',
      value: (r) => r.closeRatio,
      cell: (r) => formatPercent(r.closeRatio, 1),
      numeric: true,
    },
    { header: 'Completed', value: (r) => r.completedCards, cell: (r) => formatCount(r.completedCards), numeric: true },
    { header: 'Sales', value: (r) => r.completedSales, cell: (r) => m(r.completedSales), numeric: true },
    { header: 'ARO', value: (r) => r.averageRepairOrder, cell: (r) => m(r.averageRepairOrder), numeric: true },
  ];
  return (
    <Report query={advisors}>
      {(rows) => (
        <ReportTable
          name="service-advisors"
          label="Service advisors"
          period={period}
          columns={columns}
          rows={rows}
          rowKey={(r) => r.advisor}
          empty="No estimates were sent in this period."
          footer={
            <p className="text-sm text-muted-foreground">
              Estimates sent to customers in the period. Close ratio is the approved share of the value decided so far.
            </p>
          }
        />
      )}
    </Report>
  );
}

// ---- Technicians --------------------------------------------------------------------------

function TechnicianReport({ period, currency }: { period: Period; currency: string }) {
  const technicians = useTechnicianReport(period);
  const m = money(currency);
  const columns: Column<TechnicianRow>[] = [
    { header: 'Mechanic', value: (r) => r.mechanic, cell: (r) => <span className="font-medium">{r.mechanic}</span> },
    { header: 'Hours logged', value: (r) => r.hours, cell: (r) => formatHours(r.hours), numeric: true },
    {
      header: 'On completed jobs',
      value: (r) => r.hoursOnCompleted,
      cell: (r) => formatHours(r.hoursOnCompleted),
      numeric: true,
    },
    { header: 'Labour value', value: (r) => r.labourValue, cell: (r) => m(r.labourValue), numeric: true },
    { header: 'Job cards', value: (r) => r.jobCards, cell: (r) => formatCount(r.jobCards), numeric: true },
  ];
  return (
    <Report query={technicians}>
      {(rows) => (
        <ReportTable
          name="technicians"
          label="Technicians"
          period={period}
          columns={columns}
          rows={rows}
          rowKey={(r) => r.mechanic}
          empty="No hours were logged in this period."
          footer={
            <p className="text-sm text-muted-foreground">
              Hours by the day they were logged, including jobs still in progress.
            </p>
          }
        />
      )}
    </Report>
  );
}

// ---- Declined jobs ------------------------------------------------------------------------

function DeclinedReport({ period, currency }: { period: Period; currency: string }) {
  const declined = useDeclinedReport(period);
  const { nameOf, zoneOf, hasSeveral } = useLocationLookup();
  const m = money(currency);
  const columns: Column<DeclinedRow>[] = [
    {
      header: 'Declined',
      value: (r) => r.decidedAt,
      cell: (r) => formatDateTime(r.decidedAt, zoneOf(r.locationId)),
    },
    {
      header: 'Customer',
      value: (r) => r.customerName,
      cell: (r) => <span className="font-medium">{r.customerName}</span>,
    },
    { header: 'Vehicle', value: (r) => r.registration, cell: (r) => r.registration },
    {
      header: 'Work declined',
      value: (r) => r.description,
      cell: (r) => (
        <Link to={`/job-cards/${r.jobCardId}`} className="text-primary hover:underline">
          {r.description}
        </Link>
      ),
    },
    { header: 'Value', value: (r) => r.value, cell: (r) => m(r.value), numeric: true },
    {
      header: 'Hours',
      value: (r) => r.estimatedLabourHours,
      cell: (r) => formatHours(r.estimatedLabourHours),
      numeric: true,
    },
    { header: 'Advisor', value: (r) => r.submittedBy ?? '', cell: (r) => r.submittedBy ?? '—' },
    ...(hasSeveral
      ? [{ header: 'Location', value: (r: DeclinedRow) => nameOf(r.locationId), cell: (r: DeclinedRow) => nameOf(r.locationId) }]
      : []),
    { header: 'VIN', value: (r) => r.vin, cell: (r) => <span className="font-mono text-xs">{r.vin}</span> },
  ];
  return (
    <Report query={declined}>
      {(report) => (
        <ReportTable
          name="declined-jobs"
          label="Declined jobs"
          period={period}
          columns={columns}
          rows={report.rows}
          rowKey={(r) => r.itemId}
          empty="Nothing was declined in this period."
          footer={
            <>
              <Truncated report={report} />
              <p className="text-sm text-muted-foreground">
                Work customers turned down: a follow-up list. Total{' '}
                {m(report.rows.reduce((sum, r) => sum + r.value, 0))}.
              </p>
            </>
          }
        />
      )}
    </Report>
  );
}

// ---- Parts usage --------------------------------------------------------------------------

function PartsReport({ period, currency }: { period: Period; currency: string }) {
  const parts = usePartsReport(period);
  const { nameOf, zoneOf, hasSeveral } = useLocationLookup();
  const m = money(currency);
  const columns: Column<PartUsageRow>[] = [
    { header: 'Fitted', value: (r) => r.recordedAt, cell: (r) => formatDateTime(r.recordedAt, zoneOf(r.locationId)) },
    { header: 'Part number', value: (r) => r.partNumber, cell: (r) => <span className="font-mono text-xs">{r.partNumber}</span> },
    { header: 'Part', value: (r) => r.name, cell: (r) => r.name },
    { header: 'Qty', value: (r) => r.quantity, cell: (r) => formatCount(r.quantity), numeric: true },
    { header: 'Unit price', value: (r) => r.unitPrice, cell: (r) => m(r.unitPrice), numeric: true },
    { header: 'Total', value: (r) => r.total, cell: (r) => m(r.total), numeric: true },
    {
      header: 'Job',
      value: (r) => r.recommendation ?? '',
      cell: (r) => (
        <Link to={`/job-cards/${r.jobCardId}`} className="text-primary hover:underline">
          {r.recommendation ?? r.registration}
        </Link>
      ),
    },
    { header: 'Vehicle', value: (r) => r.registration, cell: (r) => r.registration },
    { header: 'Mechanic', value: (r) => r.recordedBy, cell: (r) => r.recordedBy },
    ...(hasSeveral
      ? [{ header: 'Location', value: (r: PartUsageRow) => nameOf(r.locationId), cell: (r: PartUsageRow) => nameOf(r.locationId) }]
      : []),
  ];
  return (
    <Report query={parts}>
      {(report) => (
        <ReportTable
          name="parts-usage"
          label="Parts usage"
          period={period}
          columns={columns}
          rows={report.rows}
          rowKey={(r) => r.id}
          empty="No parts were fitted in this period."
          footer={
            <>
              <Truncated report={report} />
              <p className="text-sm text-muted-foreground">
                {formatCount(report.rows.reduce((sum, r) => sum + r.quantity, 0))} parts,{' '}
                {m(report.rows.reduce((sum, r) => sum + r.total, 0))} at the prices charged.
              </p>
            </>
          }
        />
      )}
    </Report>
  );
}
