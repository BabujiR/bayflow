import { useEffect } from 'react';

import { PRODUCT_NAME } from '@/lib/brand';

import { foregroundFor, isHexColor } from './colors';
import { useBranding } from './use-branding';

const DEFAULT_TITLE = PRODUCT_NAME;
const DEFAULT_ICON = '/favicon.svg';
export const BRANDING_STYLE_ID = 'bayflow-branding';

/**
 * Applies the workshop's branding to the whole page: its colour (as the app's primary colour,
 * in light and dark mode), the browser tab's title and icon. Renders nothing.
 */
export function BrandingEffect() {
  const { branding, displayName } = useBranding();
  const color = isHexColor(branding.primaryColor) ? branding.primaryColor : null;

  useEffect(() => {
    if (!color) {
      return;
    }
    // A stylesheet rule rather than inline styles: `html:root` outranks both the :root and the
    // .dark palettes in index.css, so the colour applies in light and dark mode. The colour is
    // a checked #rrggbb, so nothing else can get into the rule.
    const sheet = document.createElement('style');
    sheet.id = BRANDING_STYLE_ID;
    sheet.textContent =
      `html:root { --primary: ${color}; --primary-foreground: ${foregroundFor(color)}; ` +
      `--ring: ${color}; }`;
    document.head.appendChild(sheet);
    const themeColor = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    const previousThemeColor = themeColor?.content;
    if (themeColor) {
      themeColor.content = color;
    }
    return () => {
      sheet.remove();
      if (themeColor && previousThemeColor !== undefined) {
        themeColor.content = previousThemeColor;
      }
    };
  }, [color]);

  useEffect(() => {
    if (!displayName) {
      return;
    }
    document.title = branding.showPoweredBy ? `${displayName} · ${PRODUCT_NAME}` : displayName;
    return () => {
      document.title = DEFAULT_TITLE;
    };
  }, [displayName, branding.showPoweredBy]);

  useEffect(() => {
    const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    // The square icon fits a browser tab; the logo is the fallback.
    const href = branding.iconUrl ?? branding.logoUrl;
    if (!icon || !href) {
      return;
    }
    icon.href = href;
    icon.type = 'image/png';
    return () => {
      icon.href = DEFAULT_ICON;
      icon.type = 'image/svg+xml';
    };
  }, [branding.iconUrl, branding.logoUrl]);

  return null;
}
