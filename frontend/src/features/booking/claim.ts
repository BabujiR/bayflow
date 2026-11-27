/**
 * An online booking waiting to join the customer's account: its private-link token, kept for
 * this browser tab while they sign up or sign in (CustomerProfileGate then claims it).
 */
const KEY = 'bayflow.booking-claim';

export function rememberClaim(token: string): void {
  try {
    sessionStorage.setItem(KEY, token);
  } catch {
    // Storage blocked: they can still open the link again after signing in.
  }
}

export function pendingClaim(): string | null {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function forgetClaim(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // nothing to forget
  }
}
