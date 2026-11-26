import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { avatarApi } from '@/api/endpoints';
import type { User } from '@/api/types';
import { renderApp } from '@/test/render';
import { pageOf, server, sessionFor, user } from '@/test/server';

beforeEach(() => {
  // jsdom has no object URLs: the photo preview gets a stand-in address.
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});
afterEach(() => vi.restoreAllMocks());

describe('My profile: avatar', () => {
  it('starts with initials and switches to a character', async () => {
    let sent: unknown;
    const me = user('MECHANIC', 'ana.silva');
    server.use(
      sessionFor(me),
      http.put('*/api/v1/auth/me/avatar', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json<User>({
          ...me,
          avatar: { kind: 'CHARACTER', character: 'wrench', photoUrl: null },
        });
      }),
    );
    renderApp('/profile');

    expect(
      await screen.findByRole('heading', { name: 'My profile' }, { timeout: 10_000 }),
    ).toBeInTheDocument();
    // Initials of "ana.silva".
    expect(screen.getAllByText('AS').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Save avatar' })).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: 'Character' }));
    await userEvent.click(
      within(screen.getByRole('group', { name: 'Characters' })).getByRole('button', { name: 'Wrench' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save avatar' }));

    await vi.waitFor(() => expect(sent).toEqual({ kind: 'CHARACTER', character: 'wrench' }));
    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'Save avatar' })).toBeDisabled());
  });

  it('uploads a photo', async () => {
    const me = user('SERVICE_ADVISOR', 'advisor1');
    // Multipart bodies don't survive jsdom + MSW: the call itself is checked (as for branding images).
    const upload = vi.spyOn(avatarApi, 'upload').mockResolvedValue({
      ...me,
      avatar: { kind: 'PHOTO', character: null, photoUrl: '/api/v1/tenants/current/avatars/u1?v=abc' },
    });
    server.use(sessionFor(me));
    renderApp('/profile');

    await userEvent.click(await screen.findByRole('radio', { name: 'Photo' }, { timeout: 10_000 }));
    const photo = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'me.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText('Choose a photo'), photo);
    expect(await screen.findByRole('img', { name: 'Avatar preview' })).toHaveAttribute('src', 'blob:preview');
    await userEvent.click(screen.getByRole('button', { name: 'Save avatar' }));

    // jsdom can't draw on a canvas, so the picked file itself goes (a browser sends the 512 px square).
    await vi.waitFor(() => expect(upload).toHaveBeenCalledWith(photo));
  });

  it('refuses a file that is not a photo', async () => {
    server.use(sessionFor(user('ADMIN', 'admin1')));
    renderApp('/profile');

    await userEvent.click(await screen.findByRole('radio', { name: 'Photo' }, { timeout: 10_000 }));
    const person = userEvent.setup({ applyAccept: false });
    await person.upload(
      screen.getByLabelText('Choose a photo'),
      new File(['<svg/>'], 'x.svg', { type: 'image/svg+xml' }),
    );
    expect(await screen.findByText('Choose a JPEG, PNG or WebP photo.')).toBeInTheDocument();
  });
});

describe('User accounts: avatars', () => {
  it("shows each user's avatar and lets the admin remove a photo", async () => {
    let removed = false;
    const mechanic: User = {
      ...user('MECHANIC', 'mech1'),
      id: 'u-mech',
      avatar: { kind: 'PHOTO', character: null, photoUrl: '/api/v1/tenants/current/avatars/u-mech?v=abc' },
    };
    server.use(
      sessionFor(user('ADMIN', 'admin1')),
      http.get('*/api/v1/auth/users', () => HttpResponse.json(pageOf([mechanic]))),
      http.delete('*/api/v1/auth/users/u-mech/avatar', () => {
        removed = true;
        return HttpResponse.json<User>({
          ...mechanic,
          avatar: { kind: 'INITIALS', character: null, photoUrl: null },
        });
      }),
    );
    renderApp('/users');

    await userEvent.click(
      await screen.findByRole('button', { name: "Remove mech1's photo" }, { timeout: 10_000 }),
    );
    await vi.waitFor(() => expect(removed).toBe(true));
  });
});
