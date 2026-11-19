/** Workshop addresses (slugs): the same rules as auth-service. */

/** Same rules as auth-service: 3-30 of a-z, 0-9 and single hyphens, no hyphen at either end. */
export const SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9]|-(?!-)){1,28}[a-z0-9]$/;

/** A slug suggestion from the workshop name: "Kiwi Motors Ltd." → "kiwi-motors-ltd". */
export function suggestSlug(name: string): string {
  return name
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 30)
    .replace(/-+$/, '');
}
