import { createContext, useContext } from 'react';

import type { ChangePasswordRequest, LoginRequest, RegisterRequest, Role, User } from '@/api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  login: (credentials: LoginRequest) => Promise<User>;
  /** Creates a customer account and signs it in. */
  register: (details: RegisterRequest) => Promise<User>;
  logout: () => Promise<void>;
  /** Replaces the password (and a temporary one); this session continues, others end. */
  changePassword: (request: ChangePasswordRequest) => Promise<User>;
  hasRole: (...roles: Role[]) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return value;
}
