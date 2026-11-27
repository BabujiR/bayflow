import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { authApi } from '@/api/auth';
import { configureSession, refreshSession, setAccessToken } from '@/api/http';
import type {
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  Role,
  TokenResponse,
  User,
} from '@/api/types';
import { PUBLIC_WORKSHOP_QUERY_KEY } from '@/features/workshop/use-public-workshop';

import { AuthContext, type AuthContextValue, type AuthStatus } from './auth-context';
import { effectiveRoles } from './roles';

/** Refresh this many seconds before the access token expires. */
const REFRESH_AHEAD_SECONDS = 60;

/**
 * Holds the signed-in user. The access token lives only in memory; on page load the
 * session is restored from the HttpOnly refresh cookie (POST /auth/refresh), and the
 * token is refreshed shortly before it expires.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<User | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Whether this tab has had a session: only then is there anything of the user's to forget.
  const hadSession = useRef(false);

  const clearTimer = useCallback(() => {
    if (refreshTimer.current) {
      clearTimeout(refreshTimer.current);
      refreshTimer.current = null;
    }
  }, []);

  const endSession = useCallback(() => {
    clearTimer();
    setAccessToken(null);
    setUser(null);
    setStatus('anonymous');
    if (!hadSession.current) {
      // No session to restore (a visitor without a sign-in): nothing of a user's was loaded, and
      // public pages (online booking, its private links, estimate approval) keep what they fetched.
      return;
    }
    hadSession.current = false;
    // Forget everything the session loaded, except the workshop's public info (name,
    // branding): components such as BrandingEffect keep watching it across sign-out.
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== PUBLIC_WORKSHOP_QUERY_KEY[0] });
    queryClient.getMutationCache().clear();
  }, [clearTimer, queryClient]);

  const startSession = useCallback(
    (token: TokenResponse) => {
      setAccessToken(token.accessToken);
      setUser(token.user);
      setStatus('authenticated');
      hadSession.current = true;
      clearTimer();
      const delayMs = Math.max(5, token.expiresIn - REFRESH_AHEAD_SECONDS) * 1000;
      refreshTimer.current = setTimeout(() => void refreshSession(), delayMs);
    },
    [clearTimer],
  );

  useEffect(() => {
    configureSession({
      refresh: async () => {
        try {
          startSession(await authApi.refresh());
          return true;
        } catch {
          endSession();
          return false;
        }
      },
      onExpired: endSession,
    });
    // Restore the session from the refresh cookie, if there is one.
    void refreshSession();
    return clearTimer;
  }, [startSession, endSession, clearTimer]);

  const login = useCallback(
    async (credentials: LoginRequest) => {
      const token = await authApi.login(credentials);
      startSession(token);
      return token.user;
    },
    [startSession],
  );

  const register = useCallback(
    async (details: RegisterRequest) => {
      await authApi.register(details);
      return login({ username: details.username, password: details.password });
    },
    [login],
  );

  const changePassword = useCallback(
    async (request: ChangePasswordRequest) => {
      const token = await authApi.changePassword(request);
      startSession(token);
      return token.user;
    },
    [startSession],
  );

  const resetPassword = useCallback(
    async (request: ResetPasswordRequest) => {
      const token = await authApi.resetPassword(request);
      startSession(token);
      return token.user;
    },
    [startSession],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      endSession();
    }
  }, [endSession]);

  const hasRole = useCallback(
    (...roles: Role[]) => !!user && effectiveRoles(user.roles).some((r) => roles.includes(r)),
    [user],
  );

  const updateUser = useCallback((next: User) => setUser(next), []);
  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout, changePassword, resetPassword, hasRole, updateUser }),
    [status, user, login, register, logout, changePassword, resetPassword, hasRole, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
