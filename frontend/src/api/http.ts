import { locationHeader } from '@/features/locations/selection';
import { isConsole } from '@/lib/site';

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
  /** Extra headers, e.g. Idempotency-Key. */
  headers?: Record<string, string>;
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

/** A file the API returns (an image, say), with the same sign-in handling as {@link request}. */
export async function requestBlob(path: string): Promise<Blob> {
  const options: RequestOptions = { headers: { Accept: '*/*' } };
  let response = await send(path, options);
  if (response.status === 401) {
    if (await refreshSession()) {
      response = await send(path, options);
    } else {
      sessionExpiredHandler?.();
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response.blob();
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
  const headers: Record<string, string> = {
    Accept: 'application/json',
    // "console": auth-service signs in to the Ramplight console's organization (base domain only).
    'X-Bayflow-Client': isConsole() ? 'console' : 'web',
    ...options.headers,
  };
  // FormData (file uploads) sets its own multipart Content-Type with the boundary.
  const isForm = options.body instanceof FormData;
  if (options.body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.auth !== false && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  // The location switcher (organizations with several locations): the services show only it.
  const location = locationHeader();
  if (options.auth !== false && accessToken && location) {
    headers['X-Bayflow-Location'] = location;
  }
  return fetch(url, {
    method: options.method ?? 'GET',
    headers,
    body: isForm ? (options.body as FormData) : jsonBody(options.body),
    credentials: 'same-origin',
    signal: options.signal,
  });
}

function jsonBody(body: unknown): string | undefined {
  return body !== undefined ? JSON.stringify(body) : undefined;
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
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

export { ApiError };
