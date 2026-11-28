import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { ExportInvoice, Invoice, InvoiceLine } from '@/api/types';
import { paymentsCsv, quickbooksDate, quickbooksInvoicesCsv } from '@/features/invoices/quickbooks-csv';
import { renderApp } from '@/test/render';
import { jobCard, location, noSession, pageOf, server, sessionFor, user } from '@/test/server';

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
    emailedAt: null,
    emailedTo: null,
    quickbooks: {
      status: 'NOT_SENT',
      invoiceId: null,
      syncedAt: null,
      error: null,
      paymentsToSend: 0,
      refundsByHand: 0,
    },
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

    expect(await screen.findByRole('heading', { level: 1, name: /Draft invoice/ })).toBeInTheDocument();
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
    expect(await screen.findByRole('heading', { level: 1, name: /INV-1001/ })).toBeInTheDocument();
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

  it('emails an issued invoice to the customer, or to an address typed in', async () => {
    let sentTo: unknown;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'sam')),
      serve(issued()),
      http.post(`*/api/v1/invoices/${INVOICE_ID}/email`, async ({ request }) => {
        sentTo = await request.json();
        return HttpResponse.json(
          issued({ emailedAt: '2026-11-21T10:00:00Z', emailedTo: 'accounts@example.com', version: 2 }),
        );
      }),
    );
    renderApp(`/invoices/${INVOICE_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Email invoice' }));
    const dialog = await screen.findByRole('dialog', { name: 'Email INV-1001' });
    const to = within(dialog).getByLabelText('Send to');
    expect(to).toHaveValue('ana@example.com');
    await userEvent.clear(to);
    await userEvent.type(to, 'not-an-address');
    expect(within(dialog).getByRole('button', { name: 'Send email' })).toBeDisabled();
    await userEvent.clear(to);
    await userEvent.type(to, 'accounts@example.com');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Send email' }));

    expect(await screen.findByTestId('emailed')).toHaveTextContent('Emailed to accounts@example.com');
    expect(sentTo).toEqual({ to: 'accounts@example.com' });
  });

  it('builds the QuickBooks invoice import and the payments file', () => {
    const exported: ExportInvoice = {
      id: INVOICE_ID,
      number: 'INV-1001',
      status: 'PARTIALLY_PAID',
      locationId: '00000000-0000-0000-0000-000000000001',
      customerName: 'Silva, Ana',
      customerEmail: 'ana@example.com',
      customerPhone: null,
      registration: 'BFL 1234',
      vin: '1HGCM82633A004352',
      vehicleDescription: '2019 Toyota Corolla',
      currency: 'USD',
      issuedAt: '2026-11-20T16:00:00Z',
      dueAt: '2026-12-05T16:00:00Z',
      paymentTermsDays: 15,
      lines: [
        { ...line({}) },
        {
          ...line({
            type: 'DISCOUNT',
            description: 'Loyalty',
            partNumber: null,
            quantity: 1,
            unitPrice: 10,
            lineTotal: -10,
            taxCategory: 'LABOUR',
          }),
        },
      ],
      subtotal: 80.5,
      taxTotal: 8.03,
      total: 88.53,
      amountPaid: 50,
      balanceDue: 38.53,
    };
    const csv = quickbooksInvoicesCsv([exported], 'America/New_York').split('\r\n');
    expect(csv[0]).toBe(
      'InvoiceNo,Customer,Email,InvoiceDate,DueDate,Terms,Memo,Item(Product/Service),ItemDescription,ItemQuantity,ItemRate,ItemAmount,Taxable,Currency',
    );
    expect(csv[1]).toBe(
      'INV-1001,"Silva, Ana",ana@example.com,11/20/2026,12/05/2026,Net 15,BFL 1234 · 2019 Toyota Corolla,Parts,Front brake pad set (BP-220),2,45.25,90.5,Y,USD',
    );
    expect(csv[2]).toContain(',Discount,Loyalty,1,-10,-10,Y,USD');
    expect(csv[3]).toContain(',Sales tax,Sales tax,1,8.03,8.03,N,USD');
    expect(quickbooksDate('2026-11-21T03:00:00Z', 'America/Los_Angeles')).toBe('11/20/2026');

    const payments = paymentsCsv(
      [
        {
          id: 'p1',
          invoiceId: INVOICE_ID,
          invoiceNumber: 'INV-1001',
          locationId: '00000000-0000-0000-0000-000000000001',
          customerName: 'Ana Silva',
          currency: 'USD',
          method: 'CARD',
          amount: -12.5,
          reference: '=cmd',
          receivedAt: '2026-11-21T10:00:00Z',
          receivedBy: 'sam',
        },
      ],
      'UTC',
    ).split('\r\n');
    expect(payments[0]).toBe('Date,InvoiceNo,Customer,Method,Reference,Amount,Currency,ReceivedBy');
    expect(payments[1]).toBe("11/21/2026,INV-1001,Ana Silva,Card,'=cmd,-12.5,USD,sam");
  });

  it('exports a period for the accountant (managers and admins)', async () => {
    let asked: URL | undefined;
    URL.createObjectURL = vi.fn(() => 'blob:export');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    server.use(
      sessionFor(user('MANAGER', 'maria')),
      http.get('*/api/v1/invoices', () => HttpResponse.json(pageOf([]))),
      http.get('*/api/v1/invoices/export', ({ request }) => {
        asked = new URL(request.url);
        return HttpResponse.json({ from: '', to: '', invoices: [], payments: [] });
      }),
    );
    renderApp('/invoices');

    await userEvent.click(await screen.findByRole('button', { name: 'Export' }));
    const dialog = await screen.findByRole('dialog', { name: 'Export invoices' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Download invoices CSV' }));

    expect(await within(dialog).findByRole('status')).toHaveTextContent('0 invoices exported.');
    expect(click).toHaveBeenCalled();
    expect(asked?.searchParams.get('from')).toBeTruthy();
    expect(asked?.searchParams.get('to')).toBeTruthy();
    click.mockRestore();
  });

  it('sends an issued invoice to QuickBooks and shows where it stands', async () => {
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'sam')),
      http.get('*/api/v1/quickbooks', () =>
        HttpResponse.json({
          available: true,
          connected: true,
          ready: true,
          environment: 'sandbox',
          companyName: 'Sandbox Co',
          country: 'US',
          connectedAt: '2026-11-20T10:00:00Z',
          connectedBy: 'owner',
          mapping: null,
        }),
      ),
      serve(issued()),
      http.post(`*/api/v1/invoices/${INVOICE_ID}/quickbooks`, () =>
        HttpResponse.json(
          issued({
            version: 2,
            quickbooks: {
              status: 'FAILED',
              invoiceId: null,
              syncedAt: null,
              error: 'Duplicate Name Exists Error',
              paymentsToSend: 0,
              refundsByHand: 0,
            },
          }),
        ),
      ),
    );
    renderApp(`/invoices/${INVOICE_ID}`);

    expect(await screen.findByTestId('quickbooks-state')).toHaveTextContent('Not sent to QuickBooks yet.');
    await userEvent.click(screen.getByRole('button', { name: 'Send to QuickBooks' }));

    expect(await screen.findByText('QuickBooks: Duplicate Name Exists Error')).toBeInTheDocument();
  });

  it('offers no QuickBooks button when QuickBooks is not connected', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'sam')), serve(issued()));
    renderApp(`/invoices/${INVOICE_ID}`);

    expect(await screen.findByRole('button', { name: 'Email invoice' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Send to QuickBooks' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('quickbooks-state')).not.toBeInTheDocument();
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
    expect(screen.queryByRole('button', { name: 'Email invoice' })).not.toBeInTheDocument();
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

describe('Google review requests', () => {
  it('an admin sets a location’s review link and turns requests on', async () => {
    let sent: unknown;
    server.use(
      sessionFor(user('ADMIN', 'owner')),
      http.get('*/api/v1/auth/locations', () => HttpResponse.json([location({ version: 3 })])),
      http.put('*/api/v1/auth/locations/:id/reviews', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(location({ version: 4 }));
      }),
    );
    renderApp('/locations');

    await userEvent.click(await screen.findByRole('button', { name: 'Google reviews for Main' }));
    const dialog = await screen.findByRole('dialog', { name: /Google reviews/ });
    await userEvent.click(within(dialog).getByRole('checkbox'));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save Google reviews' }));
    expect(within(dialog).getByText('Add the link to turn review requests on')).toBeInTheDocument();
    expect(sent).toBeUndefined();

    await userEvent.type(within(dialog).getByLabelText('Google review link'), 'https://g.page/r/main/review');
    await userEvent.selectOptions(within(dialog).getByLabelText('When to send it'), 'TWO_HOURS');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save Google reviews' }));

    await vi.waitFor(() =>
      expect(sent).toEqual({
        version: 3,
        reviews: { enabled: true, url: 'https://g.page/r/main/review', delay: 'TWO_HOURS' },
      }),
    );
  });

  it('shows a paid invoice’s scheduled request, which staff can stop', async () => {
    let cancelled = false;
    server.use(
      sessionFor(user('SERVICE_ADVISOR', 'sam')),
      serve(issued({ status: 'PAID', balanceDue: 0 })),
      http.get('*/api/v1/invoices/:id/review-request', () =>
        HttpResponse.json({
          status: 'SCHEDULED',
          dueAt: '2026-11-21T14:00:00Z',
          sentAt: null,
          sentTo: null,
          sentBy: null,
          reason: null,
        }),
      ),
      http.post('*/api/v1/invoices/:id/review-request/cancel', () => {
        cancelled = true;
        return HttpResponse.json({
          status: 'CANCELLED',
          dueAt: null,
          sentAt: null,
          sentTo: null,
          sentBy: null,
          reason: 'Not sent: sam chose not to',
        });
      }),
    );
    renderApp(`/invoices/${INVOICE_ID}`);

    const line = await screen.findByTestId('review-request', {}, { timeout: 10_000 });
    expect(line).toHaveTextContent(/Google review request scheduled for/);
    await userEvent.click(within(line).getByRole('button', { name: "Don't send" }));
    expect(
      await within(line).findByText('Google review request: Not sent: sam chose not to'),
    ).toBeInTheDocument();
    expect(cancelled).toBe(true);
    expect(within(line).getByRole('button', { name: 'Ask for a review' })).toBeInTheDocument();
  });

  it('lets a customer stop review requests from the e-mail link', async () => {
    let sent: unknown;
    server.use(
      noSession,
      http.post('*/api/v1/review-requests/stop', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ shopName: 'Lotus Auto' });
      }),
    );
    renderApp(`/reviews/stop?token=${'R'.repeat(43)}`);

    await userEvent.click(
      await screen.findByRole('button', { name: 'Stop review requests' }, { timeout: 10_000 }),
    );
    expect(
      await screen.findByText(/Lotus Auto won't e-mail you asking for reviews again/),
    ).toBeInTheDocument();
    expect(sent).toEqual({ token: 'R'.repeat(43) });
  });
});
