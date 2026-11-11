import { ApiError, toApiError } from './errors';

/**
 * The fetch wrapper every API call goes through.
 *
 * - Adds the in-memory access token as a Bearer header (never stored in localStorage).
 * - On 401, refreshes the session once and retries the call. Concurrent 401s share a
 *   single refresh, because a refresh token may only be used once (reuse ends the session).
 * - Turns error responses into {@link ApiError} with the Problem Details fields.
 */

type RefreshHandler = () => Promise<boolean>;

let accessToken: string | null = null;
let refreshHandler: RefreshHandler | null = null;
let sessionExpiredHandler: (() => void) | null = null;
let refreshInFlight: Promise<boolean> | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** Wired by AuthProvider: how to refresh, and what to do when the session cannot be saved. */
export function configureSession(options: { refresh: RefreshHandler; onExpired: () => void }): void {
  refreshHandler = options.refresh;
  sessionExpiredHandler = options.onExpired;
}

/** Runs the refresh handler, sharing one in-flight refresh between all callers. */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    const run = refreshHandler ? refreshHandler() : Promise.resolve(false);
    refreshInFlight = run
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

export function resetHttpForTests(): void {
  accessToken = null;
  refreshHandler = null;
  sessionExpiredHandler = null;
  refreshInFlight = null;
}

export type QueryParams = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: QueryParams;
  /** Send the access token and refresh on 401 (default true). Public auth endpoints pass false. */
  auth?: boolean;
  signal?: AbortSignal;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options);
  if (response.status === 401 && options.auth !== false) {
    if (await refreshSession()) {
      return parse<T>(await send(path, options));
    }
    sessionExpiredHandler?.();
  }
  return parse<T>(response);
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  }
  // X-Bayflow-Client: auth-service's CSRF check for the cookie endpoints (/refresh, /logout).
  // A cross-site page cannot add custom headers without a CORS preflight, which Bayflow refuses.
  const headers: Record<string, string> = { Accept: 'application/json', 'X-Bayflow-Client': 'web' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.auth !== false && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return fetch(url, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    credentials: 'same-origin',
    signal: options.signal,
  });
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const http = {
  get: <T>(path: string, query?: QueryParams, signal?: AbortSignal) => request<T>(path, { query, signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
};

export { ApiError };
