import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { browser } from '@/lib/site';
import { suggestSlug } from '@/lib/slug';
import { renderApp, renderSite } from '@/test/render';
import { noSession, problem, server } from '@/test/server';

const originalGo = browser.go;
afterEach(() => {
  browser.go = originalGo;
});

function availability(map: Record<string, { available: boolean; reason?: string }>) {
  return http.get('*/api/v1/tenants/slug-availability', ({ request }) => {
    const slug = new URL(request.url).searchParams.get('slug') ?? '';
    return HttpResponse.json({ slug, ...(map[slug] ?? { available: true }) });
  });
}

/** A working setup link (<site>/setup?invite=tok-1) for owner@example.com. */
function validInvite(shopName: string | null = null) {
  return http.post('*/api/v1/tenants/invitations/check', async ({ request }) => {
    const { token } = (await request.json()) as { token: string };
    return token === 'tok-1'
      ? HttpResponse.json({
          email: 'owner@example.com',
          contactName: 'Ana Silva',
          shopName,
          expiresAt: '2026-12-01T15:00:00Z',
        })
      : problem(400, 'invite-invalid', "This setup link isn't valid. Ask Ramplight to send you a new one.");
  });
}

async function fillSignup(form: HTMLElement, name: string) {
  await userEvent.type(within(form).getByLabelText('Workshop name'), name);
  await userEvent.type(within(form).getByLabelText('Username'), 'owner');
  expect(within(form).getByLabelText('Email')).toHaveValue('owner@example.com');
  await userEvent.type(within(form).getByLabelText('Password'), 'Correct-Horse-1');
  await userEvent.type(within(form).getByLabelText('Confirm password'), 'Correct-Horse-1');
}

describe('the public site (base domain)', () => {
  it('suggests an address from the workshop name', () => {
    expect(suggestSlug('Kiwi Motors Ltd.')).toBe('kiwi-motors-ltd');
    expect(suggestSlug('  Garage Müller & Söhne ')).toBe('garage-muller-sohne');
  });

  it('only sets up workshops with a setup link', async () => {
    renderSite('/setup');
    expect(await screen.findByRole('heading', { name: 'Ramplight is invite-only' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Set up your workshop' })).not.toBeInTheDocument();
  });

  it('explains a link that no longer works', async () => {
    server.use(
      http.post('*/api/v1/tenants/invitations/check', () =>
        problem(400, 'invite-expired', 'This setup link has expired. Ask Ramplight to send you a new one.'),
      ),
    );
    renderSite('/setup?invite=old');
    expect(await screen.findByRole('heading', { name: "This setup link doesn't work" })).toBeInTheDocument();
    expect(
      screen.getByText('This setup link has expired. Ask Ramplight to send you a new one.'),
    ).toBeInTheDocument();
  });

  it('fills in the invite and says so when the link is used up meanwhile', async () => {
    server.use(
      availability({}),
      validInvite('Lotus Auto'),
      http.post('*/api/v1/tenants/signup', () =>
        problem(400, 'invite-used', "This setup link was already used. Sign in at your workshop's address."),
      ),
    );
    renderSite('/setup?invite=tok-1');

    const form = await screen.findByRole('form', { name: 'Set up your workshop' });
    expect(screen.getByTestId('invite-for')).toHaveTextContent('Invited as owner@example.com');
    expect(within(form).getByLabelText('Workshop name')).toHaveValue('Lotus Auto');
    expect(within(form).getByLabelText('Workshop address')).toHaveValue('lotus-auto');
    await userEvent.type(within(form).getByLabelText('Username'), 'owner');
    await userEvent.type(within(form).getByLabelText('Password'), 'Correct-Horse-1');
    await userEvent.type(within(form).getByLabelText('Confirm password'), 'Correct-Horse-1');
    await within(form).findByText('lotus-auto.localhost is available');
    await userEvent.click(within(form).getByRole('button', { name: 'Create workshop' }));

    expect(
      await screen.findByText("This setup link was already used. Sign in at your workshop's address."),
    ).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Set up your workshop' })).not.toBeInTheDocument();
  });

  it('signs up a workshop and goes to its address to sign in', async () => {
    let sent: Record<string, unknown> | undefined;
    const go = vi.fn();
    browser.go = go;
    server.use(
      validInvite(),
      availability({}),
      http.post('*/api/v1/tenants/signup', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          { id: 'w1', slug: 'kiwi-motors', name: 'Kiwi Motors', adminUsername: 'owner' },
          { status: 201 },
        );
      }),
    );
    renderSite('/setup?invite=tok-1');

    const form = await screen.findByRole('form', { name: 'Set up your workshop' });
    await fillSignup(form, 'Kiwi Motors');
    expect(within(form).getByLabelText('Workshop address')).toHaveValue('kiwi-motors');
    expect(await within(form).findByText('kiwi-motors.localhost is available')).toBeInTheDocument();
    await userEvent.selectOptions(within(form).getByLabelText('Currency'), 'NZD');
    await userEvent.click(within(form).getByRole('button', { name: 'Create workshop' }));

    await vi.waitFor(() =>
      expect(sent).toMatchObject({
        workshopName: 'Kiwi Motors',
        slug: 'kiwi-motors',
        currency: 'NZD',
        adminUsername: 'owner',
        adminEmail: 'owner@example.com',
        adminPassword: 'Correct-Horse-1',
        inviteToken: 'tok-1',
      }),
    );
    expect(go).toHaveBeenCalledWith('http://kiwi-motors.localhost:3000/login?welcome=1');
  });

  it('shows taken and reserved addresses and the server’s answer on the field', async () => {
    browser.go = vi.fn();
    server.use(
      validInvite(),
      availability({
        taken: { available: false, reason: 'taken' },
        admin: { available: false, reason: 'reserved' },
      }),
      http.post('*/api/v1/tenants/signup', () => problem(409, 'slug-taken', "'race' is already taken.")),
    );
    renderSite('/setup?invite=tok-1');
    const form = await screen.findByRole('form', { name: 'Set up your workshop' });
    await fillSignup(form, 'Some Garage');
    const address = within(form).getByLabelText('Workshop address');

    fireEvent.change(address, { target: { value: 'taken' } });
    expect(await within(form).findByText('taken.localhost is already taken')).toBeInTheDocument();
    expect(within(form).getByRole('button', { name: 'Create workshop' })).toBeDisabled();

    fireEvent.change(address, { target: { value: 'admin' } });
    expect(await within(form).findByText('admin is reserved. Choose another address.')).toBeInTheDocument();

    fireEvent.change(address, { target: { value: '-bad-' } });
    await userEvent.click(within(form).getByRole('button', { name: 'Create workshop' }));
    expect(await within(form).findByText(/3-30 lower-case letters/)).toBeInTheDocument();

    // Lost the race between the check and the sign-up.
    fireEvent.change(address, { target: { value: 'race' } });
    await within(form).findByText('race.localhost is available');
    await userEvent.click(within(form).getByRole('button', { name: 'Create workshop' }));
    expect(await within(form).findByText("'race' is already taken.")).toBeInTheDocument();
  });

  it('offers a demo on the landing page, not self sign-up', async () => {
    renderSite('/');

    expect(await screen.findByRole('form', { name: /demo/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Set up your workshop|Get started/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Already on/)).not.toBeInTheDocument();
  });
});

describe("a workshop's sign-in page", () => {
  it("shows the workshop's name and the welcome after sign-up", async () => {
    server.use(noSession);
    renderApp('/login?welcome=1');

    expect(await screen.findByTestId('workshop-name')).toHaveTextContent('Bayflow Downtown');
    expect(screen.getByText(/Your workshop is ready/)).toBeInTheDocument();
  });

  it('says so when no workshop has this address', async () => {
    server.use(
      noSession,
      http.get('*/api/v1/tenants/current', () =>
        problem(404, 'workshop-not-found', 'There is no workshop here.'),
      ),
    );
    renderApp('/login');

    expect(await screen.findByRole('heading', { name: 'Workshop not found' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Request a demo' })).toHaveAttribute(
      'href',
      'http://localhost:3000/#demo',
    );
  });

  it('explains a suspended workshop', async () => {
    server.use(
      noSession,
      http.get('*/api/v1/tenants/current', () =>
        HttpResponse.json({ slug: 'demo', name: 'Demo', displayName: 'Demo', active: false }),
      ),
      http.post('*/api/v1/auth/login', () => problem(403, 'workshop-suspended', 'Suspended.')),
    );
    renderApp('/login');

    expect(await screen.findByText(/account is suspended, so nobody can sign in/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Username or email'), 'admin1');
    await userEvent.type(screen.getByLabelText('Password'), 'Bayflow!2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(
      await screen.findByText("This workshop's account is suspended. Please contact the workshop."),
    ).toBeInTheDocument();
  });
});
