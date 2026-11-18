/**
 * The location switcher's choice: one location id, or `all`. It is sent to the API on every
 * request as `X-Bayflow-Location` (the services narrow what they show to it, never widen) and
 * remembered per user in this browser.
 */
export type LocationChoice = string | 'all';

let choice: LocationChoice = 'all';
let storageKey: string | null = null;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

export function subscribeLocationChoice(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export const getLocationChoice = (): LocationChoice => choice;

export function setLocationChoice(next: LocationChoice): void {
  if (next === choice) {
    return;
  }
  choice = next;
  if (storageKey) {
    try {
      localStorage.setItem(storageKey, next);
    } catch {
      // Private windows may refuse storage; the choice still holds for this tab.
    }
  }
  emit();
}

/**
 * Restores a user's last choice after sign-in; null (sign-out) goes back to `all`. Returns true
 * if the choice changed (data loaded before it should then be fetched again).
 */
export function loadLocationChoiceFor(userId: string | null): boolean {
  storageKey = userId ? `bayflow.location.${userId}` : null;
  let stored: string | null = null;
  if (storageKey) {
    try {
      stored = localStorage.getItem(storageKey);
    } catch {
      stored = null;
    }
  }
  const next = stored || 'all';
  if (next === choice) {
    return false;
  }
  choice = next;
  emit();
  return true;
}

/** The header value for the API: nothing for `all`. */
export const locationHeader = (): string | undefined => (choice === 'all' ? undefined : choice);
