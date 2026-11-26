import type { ReactNode } from 'react';

/**
 * The built-in avatar characters (codes as auth-service knows them). Each is drawn on a 48×48
 * canvas over its own background colour; with `animated`, a gentle bob or blink (see the
 * `av-*` keyframes in index.css, off for reduced motion).
 */
export interface AvatarCharacter {
  label: string;
  background: string;
  draw: () => ReactNode;
}

const skin = '#f3c9a0';
const ink = '#1f2937';

/** Eyes that blink (the `av-blink` class) and a smile. */
const face = (y = 25) => (
  <>
    <g className="av-blink" style={{ transformOrigin: `24px ${y}px` }}>
      <circle cx="20" cy={y} r="1.6" fill={ink} />
      <circle cx="28" cy={y} r="1.6" fill={ink} />
    </g>
    <path d={`M20 ${y + 5} q4 3 8 0`} stroke={ink} strokeWidth="1.6" fill="none" strokeLinecap="round" />
  </>
);

export const AVATAR_CHARACTERS: Record<string, AvatarCharacter> = {
  mechanic: {
    label: 'Mechanic',
    background: '#dbeafe',
    draw: () => (
      <>
        <circle cx="24" cy="27" r="11" fill={skin} />
        <path d="M12 23 q12-12 24 0 v-2 q-12-12-24 0z" fill="#2563eb" />
        <rect x="11" y="21" width="26" height="3" rx="1.5" fill="#1d4ed8" />
        {face(27)}
        <path d="M14 44 q10-8 20 0" fill="#2563eb" />
      </>
    ),
  },
  advisor: {
    label: 'Service advisor',
    background: '#dcfce7',
    draw: () => (
      <>
        <path d="M14 22 q10-12 20 0 v4 h-20z" fill="#7c2d12" />
        <circle cx="24" cy="27" r="10" fill={skin} />
        <path d="M13 26 a11 11 0 0 1 22 0" stroke={ink} strokeWidth="1.8" fill="none" />
        <rect x="11" y="25" width="4" height="6" rx="2" fill={ink} />
        <path d="M15 31 q2 5 7 5" stroke={ink} strokeWidth="1.4" fill="none" />
        {face(27)}
        <path d="M14 44 q10-8 20 0" fill="#16a34a" />
      </>
    ),
  },
  manager: {
    label: 'Manager',
    background: '#ede9fe',
    draw: () => (
      <>
        <path d="M14 23 q10-13 20 0z" fill="#111827" />
        <circle cx="24" cy="27" r="10" fill={skin} />
        <rect x="16.5" y="23.5" width="6" height="4" rx="1.5" fill="none" stroke={ink} strokeWidth="1.2" />
        <rect x="25.5" y="23.5" width="6" height="4" rx="1.5" fill="none" stroke={ink} strokeWidth="1.2" />
        {face(25.5)}
        <path d="M14 44 q10-8 20 0" fill="#4c1d95" />
        <path d="M24 37 l-2 4 l2 3 l2-3z" fill="#f59e0b" />
      </>
    ),
  },
  owner: {
    label: 'Shop owner',
    background: '#fef3c7',
    draw: () => (
      <>
        <circle cx="24" cy="27" r="10" fill={skin} />
        <path d="M14 22 l3-7 l4 4 l3-6 l3 6 l4-4 l3 7z" fill="#f59e0b" />
        {face(27)}
        <path d="M14 44 q10-8 20 0" fill="#b45309" />
      </>
    ),
  },
  customer: {
    label: 'Driver',
    background: '#fce7f3',
    draw: () => (
      <>
        <path d="M13 28 q0-15 11-15 q11 0 11 15 v4 h-4 v-6 h-14 v6 h-4z" fill="#92400e" />
        <circle cx="24" cy="27" r="9.5" fill={skin} />
        {face(27)}
        <path d="M14 44 q10-8 20 0" fill="#db2777" />
      </>
    ),
  },
  wrench: {
    label: 'Wrench',
    background: '#e0f2fe',
    draw: () => (
      <g className="av-turn" style={{ transformOrigin: '24px 24px' }}>
        <path
          d="M30 12 a7 7 0 0 0-7 9 l-10 10 a3 3 0 0 0 4 4 l10-10 a7 7 0 0 0 9-7 l-4 4 l-3-1 l-1-3z"
          fill="#0369a1"
        />
      </g>
    ),
  },
  car: {
    label: 'Car',
    background: '#fee2e2',
    draw: () => (
      <g className="av-bob">
        <path d="M10 29 l3-8 q1-2 4-2 h14 q3 0 4 2 l3 8 v6 h-28z" fill="#dc2626" />
        <path d="M16 21 h16 l2 6 h-20z" fill="#bfdbfe" />
        <circle cx="16" cy="35" r="3.5" fill={ink} />
        <circle cx="32" cy="35" r="3.5" fill={ink} />
      </g>
    ),
  },
  truck: {
    label: 'Pickup',
    background: '#ecfccb',
    draw: () => (
      <g className="av-bob">
        <path d="M8 26 h18 v-6 h7 l6 6 v8 h-31z" fill="#4d7c0f" />
        <path d="M28 21 h4 l4 5 h-8z" fill="#bfdbfe" />
        <circle cx="15" cy="34" r="3.5" fill={ink} />
        <circle cx="33" cy="34" r="3.5" fill={ink} />
      </g>
    ),
  },
  bolt: {
    label: 'Spark',
    background: '#fef9c3',
    draw: () => (
      <g className="av-pulse" style={{ transformOrigin: '24px 24px' }}>
        <path d="M27 9 l-12 17 h8 l-3 13 l12-18 h-8z" fill="#eab308" />
      </g>
    ),
  },
  rocket: {
    label: 'Rocket',
    background: '#e0e7ff',
    draw: () => (
      <g className="av-bob">
        <path d="M24 9 q8 6 7 18 l-3 4 h-8 l-3-4 q-1-12 7-18z" fill="#4f46e5" />
        <circle cx="24" cy="20" r="3" fill="#c7d2fe" />
        <path d="M21 31 l3 7 l3-7z" fill="#f97316" />
      </g>
    ),
  },
  fox: {
    label: 'Fox',
    background: '#ffedd5',
    draw: () => (
      <>
        <path d="M12 14 l6 10 l-6 2z M36 14 l-6 10 l6 2z" fill="#ea580c" />
        <path d="M12 22 q12-8 24 0 l-4 12 q-8 8-16 0z" fill="#f97316" />
        <path d="M17 30 q7 9 14 0 q-7 4-14 0z" fill="#fff7ed" />
        {face(26)}
        <circle cx="24" cy="32" r="1.6" fill={ink} />
      </>
    ),
  },
  owl: {
    label: 'Owl',
    background: '#f5f5f4',
    draw: () => (
      <>
        <path d="M13 16 l5 5 h12 l5-5 v18 q-11 10-22 0z" fill="#78716c" />
        <circle cx="19" cy="25" r="5" fill="#fafaf9" />
        <circle cx="29" cy="25" r="5" fill="#fafaf9" />
        <g className="av-blink" style={{ transformOrigin: '24px 25px' }}>
          <circle cx="19" cy="25" r="2.2" fill={ink} />
          <circle cx="29" cy="25" r="2.2" fill={ink} />
        </g>
        <path d="M22.5 30 l1.5 3 l1.5-3z" fill="#f59e0b" />
      </>
    ),
  },
};

export const CHARACTER_CODES = Object.keys(AVATAR_CHARACTERS);
