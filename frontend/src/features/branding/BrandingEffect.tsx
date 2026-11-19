import { useEffect } from 'react';

import { foregroundFor, isHexColor } from './colors';
import { useBranding } from './use-branding';

const DEFAULT_TITLE = 'Bayflow';
const DEFAULT_ICON = '/favicon.svg';

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
    const style = document.documentElement.style;
    // Inline variables win over both the :root and .dark palettes in index.css.
    style.setProperty('--primary', color);
    style.setProperty('--primary-foreground', foregroundFor(color));
    style.setProperty('--ring', color);
    const themeColor = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    const previousThemeColor = themeColor?.content;
    if (themeColor) {
      themeColor.content = color;
    }
    return () => {
      style.removeProperty('--primary');
      style.removeProperty('--primary-foreground');
      style.removeProperty('--ring');
      if (themeColor && previousThemeColor !== undefined) {
        themeColor.content = previousThemeColor;
      }
    };
  }, [color]);

  useEffect(() => {
    if (!displayName) {
      return;
    }
    document.title = branding.showPoweredBy ? `${displayName} · Bayflow` : displayName;
    return () => {
      document.title = DEFAULT_TITLE;
    };
  }, [displayName, branding.showPoweredBy]);

  useEffect(() => {
    const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!icon || !branding.logoUrl) {
      return;
    }
    icon.href = branding.logoUrl;
    icon.type = 'image/png';
    return () => {
      icon.href = DEFAULT_ICON;
      icon.type = 'image/svg+xml';
    };
  }, [branding.logoUrl]);

  return null;
}
