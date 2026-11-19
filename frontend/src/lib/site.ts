import { SLUG_PATTERN } from './slug';

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

/**
 * The address of a workshop, on the same scheme and port as this page. Only a valid workshop
 * address (slug) is accepted, so text typed by a user can never point the browser elsewhere.
 */
export function workshopUrl(slug: string, path = '/'): string {
  if (!SLUG_PATTERN.test(slug)) {
    throw new Error(`Not a workshop address: ${JSON.stringify(slug)}`);
  }
  const { protocol, port } = window.location;
  return `${protocol}//${slug}.${BASE_DOMAIN}${port ? `:${port}` : ''}${safePath(path)}`;
}

/** The public site's address (landing and sign-up), on the same scheme and port. */
export function publicSiteUrl(path = '/'): string {
  const { protocol, port } = window.location;
  return `${protocol}//${BASE_DOMAIN}${port ? `:${port}` : ''}${safePath(path)}`;
}

/** A path on the same host: starts with one "/", never "//" (another host) or "\\". */
function safePath(path: string): string {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) {
    throw new Error(`Not a path: ${JSON.stringify(path)}`);
  }
  return path;
}

/**
 * Whether a URL is one of Bayflow's own addresses: http(s), on this page's port, at the base
 * domain or exactly one workshop label below it.
 */
export function isBayflowUrl(url: string): boolean {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return false;
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return false;
  }
  if (parsed.username || parsed.password || parsed.port !== window.location.port) {
    return false;
  }
  const host = parsed.hostname.toLowerCase();
  const base = BASE_DOMAIN.toLowerCase();
  return host === base || (host.endsWith(`.${base}`) && siteOf(host).kind === 'workshop');
}

/** Full-page navigation to another Bayflow address (never anywhere else); tests replace `go`. */
export const browser = {
  go: (url: string) => {
    if (!isBayflowUrl(url)) {
      throw new Error('Refusing to navigate outside Bayflow');
    }
    window.location.assign(new URL(url).href);
  },
};
