import { useState } from 'react';

import type { Avatar } from '@/api/types';
import { AVATAR_CHARACTERS } from '@/features/profile/avatar-characters';
import { cn } from '@/lib/utils';

const SIZES = { sm: 'size-8 text-xs', md: 'size-10 text-sm', lg: 'size-16 text-lg', xl: 'size-28 text-3xl' };

/** Calm background colours for initials; each name always gets the same one. */
const INITIALS_COLOURS = [
  'bg-sky-100 text-sky-800',
  'bg-emerald-100 text-emerald-800',
  'bg-amber-100 text-amber-800',
  'bg-rose-100 text-rose-800',
  'bg-violet-100 text-violet-800',
  'bg-teal-100 text-teal-800',
  'bg-orange-100 text-orange-800',
  'bg-indigo-100 text-indigo-800',
];

/** "ana.silva" → "AS", "advisor1" → "AD". */
export function initialsOf(name: string): string {
  const parts = name.split(/[\s._-]+/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : name.replace(/[^a-z0-9]/gi, '').slice(0, 2);
  return (letters || '?').toUpperCase();
}

function colourOf(name: string): string {
  let hash = 0;
  for (const ch of name) {
    hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  }
  return INITIALS_COLOURS[Math.abs(hash) % INITIALS_COLOURS.length];
}

/**
 * A user as a round picture: their photo, a built-in character, or their initials (default,
 * and whenever the photo can't load). Decorative next to their name (`alt=""`) unless `label`.
 */
export function UserAvatar({
  name,
  avatar,
  size = 'sm',
  animated = false,
  label,
  className,
}: {
  name: string;
  avatar?: Avatar | null;
  size?: keyof typeof SIZES;
  /** Characters move gently (profile and picker; not in lists). */
  animated?: boolean;
  /** Accessible name when the avatar stands alone. */
  label?: string;
  className?: string;
}) {
  const [broken, setBroken] = useState<string | null>(null);
  const character =
    avatar?.kind === 'CHARACTER' && avatar.character ? AVATAR_CHARACTERS[avatar.character] : null;
  const photo =
    avatar?.kind === 'PHOTO' && avatar.photoUrl && broken !== avatar.photoUrl ? avatar.photoUrl : null;
  const a11y = label ? { role: 'img', 'aria-label': label } : { 'aria-hidden': true };

  if (photo) {
    return (
      <img
        src={photo}
        alt={label ?? ''}
        onError={() => setBroken(photo)}
        className={cn('shrink-0 rounded-full object-cover', SIZES[size], className)}
      />
    );
  }
  if (character) {
    return (
      <span
        {...a11y}
        className={cn('inline-flex shrink-0 overflow-hidden rounded-full', SIZES[size], className)}
        style={{ background: character.background }}
      >
        <svg viewBox="0 0 48 48" className={cn('size-full', animated && 'av-animated')}>
          {character.draw()}
        </svg>
      </span>
    );
  }
  return (
    <span
      {...a11y}
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold',
        SIZES[size],
        colourOf(name),
        className,
      )}
    >
      {initialsOf(name)}
    </span>
  );
}
