import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { DeclinedRow } from '@/api/types';
import { csvField, csvFilename, toCsv } from '@/features/reports/csv';
import { describeChange, describePointChange } from '@/features/reports/format';
import { addDays, customPeriodProblem, daysBetween, startOfDay } from '@/features/reports/period';
import { trendBuckets } from '@/features/reports/SalesTrend';
import { renderApp } from '@/test/render';
import { reportSummary, server, sessionFor, user } from '@/test/server';

const declinedRow = (overrides: Partial<DeclinedRow> = {}): DeclinedRow => ({
  itemId: 'i1',
  jobCardId: 'j1',
  locationId: '00000000-0000-0000-0000-000000000001',
  customerUserId: 'u1',
  customerName: 'Ana Silva',
  registration: 'BFL 1234',
  vin: '1HGCM82633A004352',
  description: 'Replace wiper blades',
  estimatedPartsCost: 30,
  estimatedLabourHours: 0.25,
  value: 53.75,
  decidedBy: 'Ana Silva',
  decidedAt: '2026-11-20T15:00:00Z',
  submittedBy: 'sam',
  ...overrides,
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('owner dashboard (reports phase R1)', () => {
  it('is the home page of admins, with the period against the one before', async () => {
    const asked: URLSearchParams[] = [];
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/reports/summary', ({ request }) => {
        asked.push(new URL(request.url).searchParams);
        return HttpResponse.json(
          reportSummary(
            { carCount: 12, sales: 4500, averageRepairOrder: 375, closeRatio: 0.81, approvedValue: 4050, declinedValue: 950 },
            { carCount: 8, sales: 3000, averageRepairOrder: 375, closeRatio: 0.75 },
            { openCards: 5, awaitingApproval: 2, pendingValue: 640, inWork: 3 },
          ),
        );
      }),
    );
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    const period = await screen.findByRole('region', { name: 'This period' });
    const sales = within(period).getAllByTestId('kpi')[0];
    expect(sales).toHaveTextContent('Sales');
    expect(sales).toHaveTextContent('$4,500.00');
    expect(sales).toHaveTextContent('▲ 50%');
    expect(within(period).getAllByTestId('kpi')[2]).toHaveTextContent('No change');
    expect(within(period).getAllByTestId('kpi')[3]).toHaveTextContent('▲ 6 pts');

    const now = screen.getByRole('region', { name: 'In the shop now' });
    expect(now).toHaveTextContent('Waiting for approval');
    expect(now).toHaveTextContent('$640.00 estimated');

    // "This month" from the 1st, midnight in the workshop's zone (America/New_York).
    expect(asked[0].get('from')).toMatch(/^\d{4}-\d{2}-01T0[45]:00:00\.000Z$/);

    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Reports' })).toBeInTheDocument();
  });

  it('asks again when the period changes', async () => {
    const asked: string[] = [];
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/reports/summary', ({ request }) => {
        asked.push(new URL(request.url).searchParams.get('from') ?? '');
        return HttpResponse.json(reportSummary());
      }),
    );
    const { router } = renderApp('/dashboard');
    await screen.findByRole('region', { name: 'This period' });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Period' }), 'last7');
    expect(router.state.location.search).toBe('?period=last7');
    await vi.waitFor(() => expect(asked.length).toBeGreaterThan(1));
    expect(asked.at(-1)).not.toBe(asked[0]);
  });

  it('is for owners and managers only', async () => {
    server.use(sessionFor(user('MANAGER', 'manager1')));
    const manager = renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    manager.unmount();

    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    renderApp('/reports');
    expect(await screen.findByRole('heading', { name: 'Not permitted' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).queryByRole('link', { name: 'Reports' })).not.toBeInTheDocument();
  });
});

describe('reports page (reports phase R1)', () => {
  it('lists declined work and exports it as CSV', async () => {
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/reports/declined', () =>
        HttpResponse.json({ from: '', to: '', rows: [declinedRow()], truncated: false }),
      ),
    );
    let saved: Blob | undefined;
    URL.createObjectURL = vi.fn((blob: Blob) => {
      saved = blob;
      return 'blob:report';
    });
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    renderApp('/reports?tab=declined&period=last30');

    const table = await screen.findByRole('table', { name: 'Declined jobs' });
    expect(within(table).getByText('Replace wiper blades')).toBeInTheDocument();
    expect(within(table).getByText('$53.75')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Declined jobs' })).toHaveAttribute('aria-selected', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(saved).toBeDefined();
    const text = await saved!.text();
    expect(text).toContain('Declined,Customer,Vehicle,Work declined,Value,Hours,Advisor,VIN');
    expect(text).toContain('Ana Silva,BFL 1234,Replace wiper blades,53.75,0.25,sam,1HGCM82633A004352');
  });

  it('switches reports with the tabs', async () => {
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/reports/technicians', () =>
        HttpResponse.json([{ mechanic: 'mike', hours: 7.5, labourValue: 712.5, jobCards: 4, hoursOnCompleted: 6 }]),
      ),
    );
    const { router } = renderApp('/reports');
    expect(await screen.findByRole('table', { name: 'Shop summary' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Technicians' }));
    const table = await screen.findByRole('table', { name: 'Technicians' });
    expect(within(table).getByText('mike')).toBeInTheDocument();
    expect(within(table).getByText('7.5 h')).toBeInTheDocument();
    expect(router.state.location.search).toContain('tab=technicians');
  });
});

describe('report helpers', () => {
  it('writes safe CSV', () => {
    expect(csvField('Smith, "Bob"')).toBe('"Smith, ""Bob"""');
    expect(csvField('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
    expect(csvField(-12.5)).toBe('-12.5');
    expect(csvField(null)).toBe('');
    expect(toCsv([{ header: 'A', value: (r: number) => r }], [1, 2])).toBe('A\r\n1\r\n2\r\n');
    expect(csvFilename('parts', '2026-11-01', '2026-11-21')).toBe('parts-2026-11-01-to-2026-11-21.csv');
  });

  it('works out periods in the workshop calendar', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(daysBetween('2026-11-01', '2026-11-21')).toBe(21);
    expect(startOfDay('2026-11-01', 'America/New_York')).toBe('2026-11-01T04:00:00.000Z');
    expect(startOfDay('2026-11-02', 'America/New_York')).toBe('2026-11-02T05:00:00.000Z');
    expect(customPeriodProblem('2026-11-21', '2026-11-01')).toMatch(/on or before/);
    expect(customPeriodProblem('2025-01-01', '2026-11-01')).toMatch(/366 days/);
    expect(customPeriodProblem('2026-01-01', '2026-11-01')).toBeNull();
  });

  it('describes changes in words, not only colour', () => {
    expect(describeChange(150, 100)).toBe('▲ 50%');
    expect(describeChange(90, 100)).toBe('▼ 10%');
    expect(describeChange(5, 0)).toBe('New');
    expect(describePointChange(0.81, 0.75)).toBe('▲ 6 pts');
    expect(describePointChange(null, 0.75)).toBe('');
  });

  it('groups long periods by month', () => {
    const days = [{ day: '2026-02-03', carCount: 2, sales: 300, partsSales: 100, labourSales: 200 }];
    const monthly = trendBuckets(days, '2026-01-01', '2026-06-30');
    expect(monthly.map((b) => b.key)).toEqual(['2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06']);
    expect(monthly[1]).toMatchObject({ sales: 300, carCount: 2 });
    expect(trendBuckets(days, '2026-02-01', '2026-02-07')).toHaveLength(7);
  });
});
