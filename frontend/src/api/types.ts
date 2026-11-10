/**
 * Request and response shapes of the auth API.
 *
 * Milestone 6 types these by hand to match auth-service; after the first
 * `pnpm api:update` they are replaced by aliases of the generated types in ./schema.
 */

export type Role = 'CUSTOMER' | 'SERVICE_ADVISOR' | 'MECHANIC' | 'ADMIN';

export interface User {
  id: string;
  username: string;
  email: string;
  roles: Role[];
  enabled: boolean;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds until the access token expires. */
  expiresIn: number;
  expiresAt: string;
  user: User;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
