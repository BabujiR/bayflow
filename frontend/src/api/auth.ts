import { request } from './http';
import type { LoginRequest, RegisterRequest, TokenResponse, User } from './types';

const BASE = '/api/v1/auth';

/** auth-service endpoints. Public ones never send the (possibly stale) access token. */
export const authApi = {
  login: (body: LoginRequest) =>
    request<TokenResponse>(`${BASE}/login`, { method: 'POST', body, auth: false }),
  register: (body: RegisterRequest) => request<User>(`${BASE}/register`, { method: 'POST', body, auth: false }),
  /** Uses the HttpOnly refresh cookie; returns a new access token and rotates the cookie. */
  refresh: () => request<TokenResponse>(`${BASE}/refresh`, { method: 'POST', auth: false }),
  logout: () => request<void>(`${BASE}/logout`, { method: 'POST', auth: false }),
  me: () => request<User>(`${BASE}/me`),
};
