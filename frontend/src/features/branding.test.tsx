import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { brandingApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { PublicBranding } from '@/api/types';
import { colorWarning, contrast, foregroundFor } from '@/features/branding/colors';
import { renderApp } from '@/test/render';
import { branding, noSession, publicWorkshop, server, sessionFor, user } from '@/test/server';

const rootStyle = () => document.documentElement.style;

const branded = (overrides: Partial<PublicBranding> = {}) =>
  http.get('*/api/v1/tenants/current', () =>
    HttpResponse.json(
      publicWorkshop({
        displayName: 'Acme Auto',
        branding: {
          logoUrl: '/api/v1/tenants/current/logo?v=abc',
          coverUrl: '/api/v1/tenants/current/cover?v=def',
          primaryColor: '#be123c',
          showPoweredBy: false,
          banner: { text: 'Closed on 24 December', style: 'IMPORTANT' },
          ...overrides,
        },
      }),
    ),
  );

describe('brand colours', () => {
  it('picks readable text for the brand colour', () => {
    expect(foregroundFor('#1d4ed8')).toBe('#ffffff');
    expect(foregroundFor('#fde047')).toBe('#0b1020');
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 0);
    expect(colorWarning('#1d4ed8')).toBeNull();
    expect(colorWarning('#fde047')).toMatch(/light theme/);
    expect(colorWarning('#1e1b4b')).toMatch(/dark theme/);
  });
});

describe('a workshop without branding', () => {
  it('looks like Bayflow', async () => {
    server.use(noSession);
    renderApp('/login');

    expect(await screen.findByTestId('workshop-name')).toHaveTextContent('Bayflow Downtown');
    expect(screen.queryByTestId('workshop-logo')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workshop-cover')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workshop-banner')).not.toBeInTheDocument();
    expect(screen.getByTestId('powered-by')).toHaveTextContent('Powered by Bayflow');
    expect(rootStyle().getPropertyValue('--primary')).toBe('');
  });
});

describe('a branded workshop', () => {
  it('shows its logo, cover, colour and announcement on the sign-in page', async () => {
    server.use(noSession, branded());
    renderApp('/login');

    const logo = await screen.findByTestId('workshop-logo');
    expect(logo).toHaveAttribute('src', '/api/v1/tenants/current/logo?v=abc');
    expect(logo).toHaveAttribute('alt', 'Acme Auto');
    expect(screen.getByTestId('workshop-cover').querySelector('img')).toHaveAttribute(
      'src',
      '/api/v1/tenants/current/cover?v=def',
    );
    const banner = screen.getByRole('complementary', { name: 'Workshop announcement' });
    expect(banner).toHaveTextContent('Closed on 24 December');
    expect(banner).toHaveAttribute('data-style', 'IMPORTANT');
    expect(screen.queryByTestId('powered-by')).not.toBeInTheDocument();

    await vi.waitFor(() => expect(rootStyle().getPropertyValue('--primary')).toBe('#be123c'));
    expect(rootStyle().getPropertyValue('--primary-foreground')).toBe('#ffffff');
    expect(document.title).toBe('Acme Auto');
  });

  it('shows the announcement as text, never as markup', async () => {
    server.use(noSession, branded({ banner: { text: '<img src=x onerror=alert(1)>', style: 'INFO' } }));
    renderApp('/login');

    const banner = await screen.findByRole('complementary', { name: 'Workshop announcement' });
    expect(banner).toHaveTextContent('<img src=x onerror=alert(1)>');
    expect(banner.querySelector('img')).toBeNull();
  });

  it('gives customers the full announcement and staff a slim one', async () => {
    server.use(sessionFor(user('CUSTOMER', 'customer1')), branded());
    const customer = renderApp('/');
    expect(await screen.findByTestId('workshop-banner')).toHaveAttribute('data-variant', 'full');
    expect(screen.getByRole('link', { name: 'Acme Auto home' })).toBeInTheDocument();
    customer.unmount();

    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    renderApp('/board');
    expect(await screen.findByTestId('workshop-banner')).toHaveAttribute('data-variant', 'slim');
  });

  it('puts the colour back when the app closes', async () => {
    server.use(noSession, branded());
    const view = renderApp('/login');
    await vi.waitFor(() => expect(rootStyle().getPropertyValue('--primary')).toBe('#be123c'));

    view.unmount();

    expect(rootStyle().getPropertyValue('--primary')).toBe('');
    expect(document.title).toBe('Bayflow');
  });
});

describe('branding settings (workshop ADMIN)', () => {
  it('saves colour, announcement and "Powered by Bayflow", sending the version read', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      sessionFor(user('ADMIN', 'admin')),
      http.get('*/api/v1/auth/tenant/branding', () => HttpResponse.json(branding({ version: 3 }))),
      http.put('*/api/v1/auth/tenant/branding', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(branding({ primaryColor: '#be123c', bannerLive: true, version: 4 }));
      }),
    );
    renderApp('/settings');

    const form = await screen.findByRole('form', { name: 'Branding' });
    await userEvent.click(within(form).getByLabelText("Use my workshop's colour"));
    const code = within(form).getByLabelText('Colour code');
    await userEvent.clear(code);
    await userEvent.type(code, '#BE123C');
    expect(within(form).getByTestId('color-preview')).toHaveTextContent('Book a service');
    await userEvent.type(within(form).getByLabelText('Message'), 'Winter tyres in stock');
    await userEvent.selectOptions(within(form).getByLabelText('Style'), 'IMPORTANT');
    await userEvent.click(within(form).getByLabelText('Show "Powered by Bayflow"'));
    await userEvent.click(within(form).getByRole('button', { name: 'Save branding' }));

    await vi.waitFor(() =>
      expect(sent).toEqual({
        primaryColor: '#be123c',
        bannerText: 'Winter tyres in stock',
        bannerStyle: 'IMPORTANT',
        bannerStartsAt: null,
        bannerEndsAt: null,
        showPoweredBy: false,
        version: 3,
      }),
    );
  });

  it('warns about a colour that is hard to read and refuses an invalid one', async () => {
    server.use(sessionFor(user('ADMIN', 'admin')));
    renderApp('/settings');

    const form = await screen.findByRole('form', { name: 'Branding' });
    await userEvent.click(within(form).getByLabelText("Use my workshop's colour"));
    const code = within(form).getByLabelText('Colour code');
    await userEvent.clear(code);
    await userEvent.type(code, '#fde047');
    expect(await within(form).findByTestId('color-warning')).toHaveTextContent(/hard to see/i);

    await userEvent.clear(code);
    await userEvent.type(code, 'blue');
    await userEvent.type(within(form).getByLabelText('Message'), 'x');
    await userEvent.click(within(form).getByRole('button', { name: 'Save branding' }));
    expect(await within(form).findByText('A colour like #1d4ed8')).toBeInTheDocument();
  });

  it('refuses a banner that ends before it starts', async () => {
    server.use(sessionFor(user('ADMIN', 'admin')));
    renderApp('/settings');

    const form = await screen.findByRole('form', { name: 'Branding' });
    await userEvent.type(within(form).getByLabelText('Message'), 'Holiday hours');
    fireEvent.change(within(form).getByLabelText('Show from'), { target: { value: '2026-12-24T08:00' } });
    fireEvent.change(within(form).getByLabelText('Show until'), { target: { value: '2026-12-20T08:00' } });
    await userEvent.click(within(form).getByRole('button', { name: 'Save branding' }));

    expect(await within(form).findByText('Must be after the start')).toBeInTheDocument();
  });

  it('uploads a logo and shows the server refusing an unsafe file', async () => {
    const upload = vi.spyOn(brandingApi, 'upload').mockImplementation(async ({ file }) => {
      if (file.name === 'fake.png') {
        throw new ApiError(415, {
          type: 'urn:bayflow:problem:unsupported-image',
          detail: 'Upload a PNG or JPEG image.',
        });
      }
      return branding({ logoUrl: '/api/v1/tenants/current/logo?v=new' });
    });
    server.use(sessionFor(user('ADMIN', 'admin')));
    renderApp('/settings');
    const person = userEvent.setup({ applyAccept: false });

    const logo = await screen.findByTestId('branding-logo');
    expect(within(logo).getByText('Bayflow logo shown')).toBeInTheDocument();

    // Checked in the browser first: not an image, so nothing is sent.
    await person.upload(
      within(logo).getByLabelText('Upload logo'),
      new File(['hello'], 'notes.txt', { type: 'text/plain' }),
    );
    expect(within(logo).getByRole('alert')).toHaveTextContent('Choose a PNG or JPEG image.');
    expect(upload).not.toHaveBeenCalled();

    // Claims to be a PNG; the server checks the content and refuses it.
    await person.upload(
      within(logo).getByLabelText('Upload logo'),
      new File(['<svg/>'], 'fake.png', { type: 'image/png' }),
    );
    expect(await within(logo).findByRole('alert')).toHaveTextContent('Upload a PNG or JPEG image.');

    await person.upload(
      within(logo).getByLabelText('Upload logo'),
      new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'logo.png', { type: 'image/png' }),
    );
    expect(await within(logo).findByRole('img', { name: 'Current logo' })).toHaveAttribute(
      'src',
      '/api/v1/tenants/current/logo?v=new',
    );
    expect(upload).toHaveBeenLastCalledWith(expect.objectContaining({ kind: 'logo' }));
    upload.mockRestore();
  });

  it('is not offered to staff', async () => {
    server.use(sessionFor(user('SERVICE_ADVISOR', 'advisor1')));
    renderApp('/settings');
    expect(await screen.findByText('Not permitted')).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Branding' })).not.toBeInTheDocument();
  });
});
