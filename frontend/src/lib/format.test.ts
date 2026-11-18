import { afterEach, describe, expect, it } from 'vitest';

import {
  currencySymbol,
  formatTime,
  isoToLocalInput,
  localInputToIso,
  setWorkshopZone,
  todayIso,
} from './format';

describe("dates in the workshop's time zone", () => {
  afterEach(() => setWorkshopZone(undefined));

  it("uses the workshop's day, not the browser's", () => {
    // 15:00 UTC on 17 Nov is already 18 Nov in Auckland, still 17 Nov in New York.
    const now = new Date('2026-11-17T15:00:00Z');
    setWorkshopZone('Pacific/Auckland');
    expect(todayIso(now)).toBe('2026-11-18');
    setWorkshopZone('America/New_York');
    expect(todayIso(now)).toBe('2026-11-17');
  });

  it('turns workshop wall-clock time into the right instant and back', () => {
    setWorkshopZone('America/New_York');
    expect(localInputToIso('2030-01-15T10:30')).toBe('2030-01-15T15:30:00.000Z'); // EST
    expect(localInputToIso('2030-07-15T10:30')).toBe('2030-07-15T14:30:00.000Z'); // EDT
    expect(isoToLocalInput('2030-01-15T15:30:00Z')).toBe('2030-01-15T10:30');

    setWorkshopZone('Pacific/Auckland');
    expect(localInputToIso('2030-01-15T10:30')).toBe('2030-01-14T21:30:00.000Z'); // NZDT, +13
    expect(isoToLocalInput('2030-01-14T21:30:00Z')).toBe('2030-01-15T10:30');
  });

  it('treats an empty or partial input as not a time instead of throwing', () => {
    setWorkshopZone('America/New_York');
    expect(localInputToMillis('')).toBeNaN();
    expect(localInputToMillis('2030-01-15')).toBeNaN();
    setWorkshopZone(undefined);
    expect(localInputToMillis('')).toBeNaN();
  });

  it('handles the day clocks go forward', () => {
    setWorkshopZone('America/New_York');
    // 8 Mar 2026: 02:00 jumps to 03:00. 09:00 that day is EDT.
    expect(localInputToIso('2026-03-08T09:00')).toBe('2026-03-08T13:00:00.000Z');
    expect(localInputToIso('2026-03-08T01:00')).toBe('2026-03-08T06:00:00.000Z');
  });

  it('formats times in the workshop zone', () => {
    setWorkshopZone('UTC');
    const utc = formatTime('2030-01-15T15:30:00Z');
    setWorkshopZone('Asia/Kolkata');
    const india = formatTime('2030-01-15T15:30:00Z');
    expect(utc).not.toBe(india);
    expect(india).toMatch(/9:00/);
  });

  it('gives currency symbols', () => {
    expect(currencySymbol('USD')).toBe('$');
    expect(currencySymbol('EUR')).toBe('€');
    expect(currencySymbol('GBP')).toBe('£');
  });
});
