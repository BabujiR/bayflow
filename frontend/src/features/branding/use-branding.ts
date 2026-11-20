import type { PublicBranding } from '@/api/types';
import { usePublicWorkshop } from '@/features/workshop/use-public-workshop';

export const NO_BRANDING: PublicBranding = {
  logoUrl: null,
  coverUrl: null,
  primaryColor: null,
  showPoweredBy: true,
  announcements: [],
};

/** The look of the workshop at this address (Bayflow's while loading or when it has none). */
export function useBranding(): { branding: PublicBranding; displayName: string | null } {
  const workshop = usePublicWorkshop();
  return {
    // Older servers (or a missing list) mean no announcements.
    branding: workshop.data?.branding
      ? { ...workshop.data.branding, announcements: workshop.data.branding.announcements ?? [] }
      : NO_BRANDING,
    displayName: workshop.data?.displayName ?? null,
  };
}
