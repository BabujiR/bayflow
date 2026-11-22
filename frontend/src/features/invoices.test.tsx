import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { Invoice, InvoiceLine } from '@/api/types';
import { renderApp } from '@/test/render';
import { jobCard, location, pageOf, server, sessionFor, user } from '@/test/server';

const INVOICE_ID = 'i0000000-0000-0000-0000-000000000001';
const CARD_ID = 'j0000000-0000-0000-0000-000000000001';

const line = (overrides: Partial<InvoiceLine>): InvoiceLine => ({
  id: 'l1',
  type: 'PART',
  feeCode: null,
  description: 'Front brake pad set',
  partNumber: 'BP-220',
  quantity: 2,
  unitPrice: 45.25,
  unitCost: null,
  lineTotal: 90.5,
  taxable: true,
  taxCategory: 'PARTS',
  fromJobCard: true,
  ...overrides,
});

function invoice(overrides: Partial<Invoice> = {}): Invoice {
  return {
    id: INVOICE_ID,
    number: null,
    status: 'DRAFT',
    jobCardId: CARD_ID,
    locationId: '00000000-0000-0000-0000-000000000001',
    customerUserId: '00000000-0000-0000-0000-000000000001',
    customerName: 'Ana Silva',
    customerEmail: 'ana@example.com',
    customerPhone: '+1 555 0100',
    registration: 'BFL 1234',
    vin: '1HGCM82633A004352',
    mileage: 42000,
    vehicleDescription: '2019 Toyota Corolla',
    currency: 'USD',
    lines: [
      line({}),
      line({
        id: 'l2',
        type: 'LABOUR',
        description: 'Labour: Replace front pads',
        partNumber: null,
        quantity: 1.25,
        unitPrice: 95,
        lineTotal: 118.75,
        taxCategory: 'LABOUR',
      }),
    ],
    partsTotal: 90.5,
    labourTotal: 118.75,
    feesTotal: 0,
    discountTotal: 0,
    subtotal: 209.25,
    taxes: [
      { category: 'PARTS', rate: 8.875, tax: 8.03 },
      { category: 'LABOUR', rate: 0, tax: 0 },
      { category: 'FEES', rate: 8.875, tax: 0 },
    ],
    taxTotal: 8.03,
    total: 217.28,
    amountPaid: 0,
    balanceDue: 217.28,
    payments: [],
    notes: null,
    footer: '12-month warranty',
    paymentTermsDays: 0,
    issuedAt: null,
    issuedBy: null,
    dueAt: null,
    voidedAt: null,
    voidedBy: null,
    voidReason: null,
    createdAt: '2026-11-20T15:00:00Z',
    createdBy: 'sam',
    partsProfit: { partsWithCost: 0, partsCost: 0, profit: 0 },
    version: 0,
    ...overrides,
  };
}

const issued = (overrides: Partial<Invoice> = {}) =>
  invoice({
    number: 'INV-1001',
    status: 'ISSUED',
    issuedAt: '2026-11-20T16:00:00Z',
    issuedBy: 'sam',
    dueAt: '2026-11-20T16:00:00Z',
    version: 1,
    ...overrides,
  });

const serve = (i: Invoice) => http.get(`*/api/v1/invoices/${i.id}`, () => HttpResponse.json(i));

describe('invoices (R2a)', () => {
  it('lets an advisor create the invoice of a completed job', async () => {
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'sam')),
      http.get(`*/api/v1/job-cards/${CARD_ID}`, () => HttpResponse.json(jobCard({ status: 'COMPLETED' }))),
      http.get(`*/api/v1/job-cards/${CARD_ID}/invoice`, () =>
        HttpResponse.json({ status: 404, title: 'Not Found' }, { status: 404 }),
      ),
      http.post(`*/api/v1/job-cards/${CARD_ID}/invoice`, () => HttpResponse.json(invoice(), { status: 201 })),
      serve(invoice()),
    );
    const { router } = renderApp(`/job-cards/${CARD_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Create invoice' }));

    expect(await screen.findByRole('heading', { name: /Draft invoice/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/invoices/${INVOICE_ID}`);
  });

  it('edits a draft, then issues it', async () => {
    let saved: Record<string, unknown> | undefined;
    let issuedWith: unknown;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'sam')),
      serve(invoice()),
      http.put(`*/api/v1/invoices/${INVOICE_ID}`, async ({ request }) => {
        saved = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(invoice({ version: 1, discountTotal: 10, total: 207.28 }));
      }),
      http.post(`*/api/v1/invoices/${INVOICE_ID}/issue`, async ({ request }) => {
        issuedWith = await request.json();
        return HttpResponse.json(issued({ version: 2 }));
      }),
    );
    renderApp(`/invoices/${INVOICE_ID}`);

    const cost = await screen.findByLabelText('Unit cost of Front brake pad set');
    await userEvent.type(cost, '30');
    await userEvent.click(screen.getByRole('button', { name: 'Add discount' }));
    const off = screen.getByLabelText('Amount off (Discount)');
    await userEvent.clear(off);
    await userEvent.type(off, '10');
    // Unsaved: issuing waits for the save.
    expect(screen.getByRole('button', { name: 'Issue invoice' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await vi.waitFor(() => expect(saved).toBeDefined());
    expect(saved).toMatchObject({
      version: 0,
      lines: [
        { id: 'l1', unitCost: 30, quantity: 2, unitPrice: 45.25 },
        { id: 'l2', unitCost: null },
        { type: 'DISCOUNT', description: 'Discount', unitPrice: 10, taxCategory: 'LABOUR' },
      ],
    });

    await userEvent.click(await screen.findByRole('button', { name: 'Issue invoice' }));
    expect(await screen.findByRole('heading', { name: /INV-1001/ })).toBeInTheDocument();
    expect(issuedWith).toEqual({ version: 1 });
  });

  it('records a payment once, with an idempotency key', async () => {
    const keys: (string | null)[] = [];
    let paid: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'sam')),
      serve(issued()),
      http.post(`*/api/v1/invoices/${INVOICE_ID}/payments`, async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        paid = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          issued({
            status: 'PAID',
            amountPaid: 217.28,
            balanceDue: 0,
            payments: [
              {
                id: 'p1',
                method: 'CASH',
                amount: 217.28,
                reference: null,
                receivedAt: '2026-11-20T17:00:00Z',
                receivedBy: 'sam',
              },
            ],
          }),
        );
      }),
    );
    renderApp(`/invoices/${INVOICE_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }));
    const dialog = await screen.findByRole('dialog', { name: /Record payment/ });
    expect(within(dialog).getByLabelText('Amount')).toHaveValue('217.28');
    await userEvent.selectOptions(within(dialog).getByLabelText('Method'), 'CASH');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));

    expect(await screen.findByTestId('invoice-status')).toHaveTextContent('Paid');
    expect(paid).toEqual({ method: 'CASH', amount: 217.28 });
    expect(keys).toHaveLength(1);
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/);
    // Advisors don't void or refund.
    expect(screen.queryByRole('button', { name: 'Void' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Refund' })).not.toBeInTheDocument();
  });

  it('lets a manager void an unpaid invoice with a reason', async () => {
    let reason: unknown;
    server.use(
      sessionFor(user('MANAGER', 'maria')),
      serve(issued()),
      http.post(`*/api/v1/invoices/${INVOICE_ID}/void`, async ({ request }) => {
        reason = await request.json();
        return HttpResponse.json(issued({ status: 'VOID', voidReason: 'Wrong customer', balanceDue: 0 }));
      }),
    );
    renderApp(`/invoices/${INVOICE_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Void' }));
    const dialog = await screen.findByRole('dialog', { name: 'Void INV-1001?' });
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Wrong customer');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Void invoice' }));

    expect(await screen.findByText(/This invoice was voided: Wrong customer/)).toBeInTheDocument();
    expect(reason).toEqual({ reason: 'Wrong customer' });
  });

  it('shows customers their invoices without staff actions', async () => {
    server.use(
      sessionFor(user('CUSTOMER', 'ana')),
      http.get('*/api/v1/invoices', () =>
        HttpResponse.json(
          pageOf([
            {
              id: INVOICE_ID,
              number: 'INV-1001',
              status: 'ISSUED',
              jobCardId: CARD_ID,
              locationId: '00000000-0000-0000-0000-000000000001',
              customerName: 'Ana Silva',
              registration: 'BFL 1234',
              currency: 'USD',
              total: 217.28,
              balanceDue: 217.28,
              issuedAt: '2026-11-20T16:00:00Z',
              dueAt: '2026-11-20T16:00:00Z',
              createdAt: '2026-11-20T15:00:00Z',
            },
          ]),
        ),
      ),
      serve(issued({ partsProfit: null })),
    );
    renderApp('/invoices');

    expect(await screen.findByRole('heading', { name: 'My invoices' })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('link', { name: 'INV-1001' }));

    const document_ = await screen.findByTestId('invoice-document');
    expect(within(document_).getByTestId('invoice-total')).toHaveTextContent('$217.28');
    expect(within(document_).getByText('12-month warranty')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Print / Save as PDF' })).toHaveAttribute(
      'href',
      `/invoices/${INVOICE_ID}/print`,
    );
    expect(screen.queryByRole('button', { name: 'Record payment' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Job card' })).not.toBeInTheDocument();
  });

  it('prints the invoice alone', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'sam')), serve(issued()));
    renderApp(`/invoices/${INVOICE_ID}/print`);

    const document_ = await screen.findByTestId('invoice-document');
    expect(within(document_).getByRole('heading', { name: 'INV-1001' })).toBeInTheDocument();
    expect(within(document_).getByText('Tax on parts (8.875 %)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Print / Save as PDF' })).toBeInTheDocument();
    expect(screen.queryByTestId('sidebar')).not.toBeInTheDocument();
  });

  it("lets the admin set a location's tax and fees", async () => {
    let sent: unknown;
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/auth/locations', () => HttpResponse.json([location({ version: 3 })])),
      http.put('*/api/v1/auth/locations/:id/billing', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(location({ version: 4 }));
      }),
    );
    renderApp('/locations');

    await userEvent.click(await screen.findByRole('button', { name: 'Tax and fees for Main' }));
    const dialog = await screen.findByRole('dialog', { name: /Tax and fees/ });
    const parts = within(dialog).getByLabelText('Parts');
    await userEvent.clear(parts);
    await userEvent.type(parts, '8.875');
    await userEvent.type(within(dialog).getByLabelText('Shop supplies (% of labour)'), '5');
    await userEvent.type(within(dialog).getByLabelText('Disposal fee'), '3.50');
    await userEvent.selectOptions(within(dialog).getByLabelText('Payment terms'), '15');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save tax and fees' }));

    await vi.waitFor(() => expect(sent).toBeDefined());
    expect(sent).toEqual({
      version: 3,
      billing: {
        partsTaxRate: 8.875,
        labourTaxRate: 0,
        feesTaxRate: 0,
        shopSuppliesPercent: 5,
        shopSuppliesCap: null,
        disposalFee: 3.5,
        paymentTermsDays: 15,
        invoiceFooter: null,
      },
    });
  });
});
