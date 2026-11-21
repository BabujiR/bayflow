import { request } from './http';
import type {
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  TokenResponse,
  User,
} from './types';

const BASE = '/api/v1/auth';

/** auth-service endpoints. Public ones never send the (possibly stale) access token. */
export const authApi = {
  login: (body: LoginRequest) =>
    request<TokenResponse>(`${BASE}/login`, { method: 'POST', body, auth: false }),
  register: (body: RegisterRequest) =>
    request<User>(`${BASE}/register`, { method: 'POST', body, auth: false }),
  /** Uses the HttpOnly refresh cookie; returns a new access token and rotates the cookie. */
  refresh: () => request<TokenResponse>(`${BASE}/refresh`, { method: 'POST', auth: false }),
  logout: () => request<void>(`${BASE}/logout`, { method: 'POST', auth: false }),
  me: () => request<User>(`${BASE}/me`),
  /** E-mails a reset link if the address has an account; answers the same either way. */
  forgotPassword: (body: ForgotPasswordRequest) =>
    request<void>(`${BASE}/password/forgot`, { method: 'POST', body, auth: false }),
  /** Sets the new password with the link's token and signs in (new cookie). */
  resetPassword: (body: ResetPasswordRequest) =>
    request<TokenResponse>(`${BASE}/password/reset`, { method: 'POST', body, auth: false }),
  /** Ends the user's other sessions; returns this one's new token (and sets a new cookie). */
  changePassword: (body: ChangePasswordRequest) =>
    request<TokenResponse>(`${BASE}/me/password`, { method: 'POST', body }),
};
