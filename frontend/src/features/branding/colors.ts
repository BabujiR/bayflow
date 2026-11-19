/** Colour maths for a workshop's brand colour (WCAG 2 relative luminance and contrast). */

const HEX = /^#[0-9a-f]{6}$/i;

export const isHexColor = (value: string | null | undefined): value is string => !!value && HEX.test(value);

/** Text on the brand colour: white or near-black, whichever reads better. */
export const LIGHT_TEXT = '#ffffff';
export const DARK_TEXT = '#0b1020';

/** The app's own page backgrounds, to warn about a colour that would be hard to see on them. */
const LIGHT_PAGE = '#fafbfc';
const DARK_PAGE = '#141824';

function channel(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function luminance(hex: string): number {
  const n = Number.parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 0xff) + 0.7152 * channel((n >> 8) & 0xff) + 0.0722 * channel(n & 0xff);
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

/** The text colour for buttons and badges filled with {@code hex}. */
export function foregroundFor(hex: string): string {
  return contrast(hex, LIGHT_TEXT) >= contrast(hex, DARK_TEXT) ? LIGHT_TEXT : DARK_TEXT;
}

/**
 * Why a brand colour may be hard to read, or null when it is fine: button text needs 4.5:1, and
 * links and outlines in the colour need 3:1 on both the light and the dark page.
 */
export function colorWarning(hex: string): string | null {
  if (contrast(hex, foregroundFor(hex)) < 4.5) {
    return 'Text on buttons in this colour is hard to read. Choose a darker or lighter shade.';
  }
  if (contrast(hex, LIGHT_PAGE) < 3) {
    return 'Links in this colour are hard to see on the light theme. Choose a darker shade.';
  }
  if (contrast(hex, DARK_PAGE) < 3) {
    return 'Links in this colour are hard to see on the dark theme. Choose a lighter shade.';
  }
  return null;
}
