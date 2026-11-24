import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderSite } from '@/test/render';
import { problem, server } from '@/test/server';

describe('request a demo (public site)', () => {
  it('asks for the required details and sends the request', async () => {
    let sent: Record<string, unknown> | undefined;
    server.use(
      http.post('*/api/v1/tenants/demo-requests', async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'd1' }, { status: 201 });
      }),
    );
    renderSite('/');

    const form = await screen.findByRole('form', { name: 'Request a demo' });
    await userEvent.click(within(form).getByRole('button', { name: 'Request a demo' }));
    expect(await within(form).findByText('Enter your first name')).toBeInTheDocument();
    expect(within(form).getByText('Choose how many locations')).toBeInTheDocument();
    expect(sent).toBeUndefined();

    await userEvent.type(within(form).getByLabelText('First name'), 'Ana');
    await userEvent.type(within(form).getByLabelText('Last name'), 'Silva');
    await userEvent.type(within(form).getByLabelText('Email'), 'ana@example.com');
    await userEvent.type(within(form).getByLabelText('Phone'), '+1 555 010 0199');
    await userEvent.type(within(form).getByLabelText('Shop or group name'), 'Lotus Auto Group');
    expect(within(form).queryByLabelText('Shop type')).not.toBeInTheDocument();
    await userEvent.selectOptions(within(form).getByLabelText('How many locations?'), '4-10');
    await userEvent.click(within(form).getByRole('button', { name: 'Request a demo' }));

    expect(await screen.findByRole('status')).toHaveTextContent('ana@example.com');
    expect(sent).toEqual({
      firstName: 'Ana',
      lastName: 'Silva',
      email: 'ana@example.com',
      phone: '+1 555 010 0199',
      shopName: 'Lotus Auto Group',
      locations: '4-10',
      city: null,
      message: null,
    });
  });

  it('says so when there were too many requests', async () => {
    server.use(
      http.post('*/api/v1/tenants/demo-requests', () => problem(429, 'too-many-requests', 'Slow down')),
    );
    renderSite('/');

    const form = await screen.findByRole('form', { name: 'Request a demo' });
    await userEvent.type(within(form).getByLabelText('First name'), 'Ana');
    await userEvent.type(within(form).getByLabelText('Last name'), 'Silva');
    await userEvent.type(within(form).getByLabelText('Email'), 'ana@example.com');
    await userEvent.type(within(form).getByLabelText('Phone'), '5550100199');
    await userEvent.type(within(form).getByLabelText('Shop or group name'), 'Lotus');
    await userEvent.selectOptions(within(form).getByLabelText('How many locations?'), '1');
    await userEvent.click(within(form).getByRole('button', { name: 'Request a demo' }));

    expect(await within(form).findByRole('alert')).toHaveTextContent('Too many requests');
  });
});
