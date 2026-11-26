import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { withErrorPage } from '@/components/common/RouteError';

function Broken(): never {
  throw new Error('boom');
}

describe('route error page', () => {
  it('shows a friendly page instead of a crash when a page fails to render', async () => {
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => {});
    const router = createMemoryRouter(withErrorPage([{ path: '/', element: <Broken /> }]));
    render(<RouterProvider router={router} />);

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload the page' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to the start' })).toHaveAttribute('href', '/');
    quiet.mockRestore();
  });
});
