/**
 * Which part of Bayflow this address serves (SaaS phase 4):
 *
 * - the bare base domain (`localhost:3000`, later e.g. `bayflow.app`) is the public site:
 *   the landing page and workshop sign-up;
 * - `<slug>.<base domain>` (`demo.localhost:3000`) is one workshop's app.
 *
 * The services decide the workshop from the same address, so the app never sends it itself.
 */
export type Site = { kind: 'public' } | { kind: 'workshop'; slug: string };

/** The base domain; `VITE_BASE_DOMAIN` at build time, `localhost` by default. */
export const BASE_DOMAIN: string = (import.meta.env.VITE_BASE_DOMAIN as string | undefined) || 'localhost';

/** The site for a host name such as `acme.localhost` (no port). */
export function siteOf(hostname: string, baseDomain: string = BASE_DOMAIN): Site {
  const host = hostname.toLowerCase().replace(/\.$/, '');
  const base = baseDomain.toLowerCase();
  if (host.endsWith(`.${base}`)) {
    const label = host.slice(0, -(base.length + 1));
    if (label && !label.includes('.') && label !== 'www') {
      return { kind: 'workshop', slug: label };
    }
  }
  return { kind: 'public' };
}

export const currentSite = (): Site => siteOf(window.location.hostname);

/** The address of a workshop, on the same scheme and port as this page. */
export function workshopUrl(slug: string, path = '/'): string {
  const { protocol, port } = window.location;
  return `${protocol}//${slug}.${BASE_DOMAIN}${port ? `:${port}` : ''}${path}`;
}

/** The public site's address (landing and sign-up), on the same scheme and port. */
export function publicSiteUrl(path = '/'): string {
  const { protocol, port } = window.location;
  return `${protocol}//${BASE_DOMAIN}${port ? `:${port}` : ''}${path}`;
}

/** Full-page navigation to another address; tests replace `go`. */
export const browser = {
  go: (url: string) => window.location.assign(url),
};
