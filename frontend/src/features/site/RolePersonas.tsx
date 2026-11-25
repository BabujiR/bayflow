import type { SVGProps } from 'react';

/**
 * The landing page's role characters ("Made for the whole shop"): a small illustrated person per
 * role, who blinks and bobs, with a prop that shows what the role does. The motion lives in
 * index.css (.rp-*) and stops for people who ask their device for reduced motion. Decorative: the
 * card's title names the role.
 */
type PersonaProps = SVGProps<SVGSVGElement>;

function Frame({ children, ...props }: PersonaProps) {
  return (
    <svg viewBox="0 0 96 96" aria-hidden="true" {...props}>
      {children}
    </svg>
  );
}

/** Owner: a sales chart grows beside her. */
export function OwnerPersona(props: PersonaProps) {
  return (
    <Frame {...props}>
      <circle cx="48" cy="48" r="46" fill="#eef0ff" />
      <g className="rp-bob">
        <path d="M18 92c2-18 14-26 30-26s28 8 30 26z" fill="#2b3bd1" />
        <path d="M42 66l6 10 6-10z" fill="#ffffff" />
        <rect x="42" y="54" width="12" height="12" rx="4" fill="#c68863" />
        <circle cx="48" cy="40" r="16" fill="#c68863" />
        <path d="M32 40c0-12 8-18 16-18s16 6 16 18c-4-6-10-9-16-9s-12 3-16 9z" fill="#2a1d18" />
        <circle cx="48" cy="20" r="6" fill="#2a1d18" />
        <ellipse className="rp-eye" cx="42" cy="42" rx="1.8" ry="2.2" fill="#1e293b" />
        <ellipse className="rp-eye" cx="54" cy="42" rx="1.8" ry="2.2" fill="#1e293b" />
        <path d="M44 49q4 3 8 0" stroke="#7c3f2a" strokeWidth="1.8" fill="none" strokeLinecap="round" />
      </g>
      <rect x="62" y="62" width="28" height="24" rx="5" fill="#ffffff" stroke="#c7cdf7" />
      <rect className="rp-bar" x="67" y="74" width="4" height="8" rx="1" fill="#94a3b8" />
      <rect className="rp-bar rp-d1" x="74" y="70" width="4" height="12" rx="1" fill="#2b3bd1" />
      <rect className="rp-bar rp-d2" x="81" y="66" width="4" height="16" rx="1" fill="#14b8a6" />
    </Frame>
  );
}

/** Location manager: two map pins hop in turn. */
export function ManagerPersona(props: PersonaProps) {
  return (
    <Frame {...props}>
      <circle cx="48" cy="48" r="46" fill="#ecfdf5" />
      <g className="rp-bob">
        <path d="M18 92c2-18 14-26 30-26s28 8 30 26z" fill="#0d9488" />
        <path d="M41 66h14l-7 8z" fill="#0f766e" />
        <rect x="42" y="54" width="12" height="12" rx="4" fill="#f1c7a3" />
        <circle cx="48" cy="40" r="16" fill="#f1c7a3" />
        <path d="M32 38c0-11 7-16 16-16s16 5 16 16c-2-4-6-7-10-7h-12c-4 0-8 3-10 7z" fill="#8a5a2b" />
        <ellipse className="rp-eye rp-e2" cx="42" cy="42" rx="1.8" ry="2.2" fill="#1e293b" />
        <ellipse className="rp-eye rp-e2" cx="54" cy="42" rx="1.8" ry="2.2" fill="#1e293b" />
        <path d="M44 49q4 3 8 0" stroke="#9a5b3a" strokeWidth="1.8" fill="none" strokeLinecap="round" />
      </g>
      <g className="rp-hop">
        <path d="M74 78s7-6.5 7-11.5a7 7 0 0 0-14 0c0 5 7 11.5 7 11.5z" fill="#2b3bd1" />
        <circle cx="74" cy="66.5" r="2.6" fill="#ffffff" />
      </g>
      <g className="rp-hop rp-delay-6">
        <path d="M86 86s4.5-4 4.5-7.5a4.5 4.5 0 0 0-9 0c0 3.5 4.5 7.5 4.5 7.5z" fill="#14b8a6" />
      </g>
    </Frame>
  );
}

/** Service advisor: sound waves pulse from the headset. */
export function AdvisorPersona(props: PersonaProps) {
  return (
    <Frame {...props}>
      <circle cx="48" cy="48" r="46" fill="#fff7ed" />
      <g className="rp-bob">
        <path d="M18 92c2-18 14-26 30-26s28 8 30 26z" fill="#f97316" />
        <rect x="42" y="54" width="12" height="12" rx="4" fill="#8d5a3b" />
        <circle cx="48" cy="40" r="16" fill="#8d5a3b" />
        <path
          d="M30 42c-2-14 6-22 18-22s20 8 18 22c-2-3-3-8-6-10-4 3-16 3-24 0-3 2-4 7-6 10z"
          fill="#1c1917"
        />
        <circle cx="31" cy="34" r="5" fill="#1c1917" />
        <circle cx="65" cy="34" r="5" fill="#1c1917" />
        <ellipse className="rp-eye rp-e3" cx="42" cy="42" rx="1.8" ry="2.2" fill="#0f172a" />
        <ellipse className="rp-eye rp-e3" cx="54" cy="42" rx="1.8" ry="2.2" fill="#0f172a" />
        <path d="M44 49q4 3 8 0" stroke="#4a2c1a" strokeWidth="1.8" fill="none" strokeLinecap="round" />
        <path d="M31 40a17 17 0 0 1 34 0" fill="none" stroke="#334155" strokeWidth="2.5" />
        <rect x="28" y="38" width="6" height="10" rx="3" fill="#334155" />
        <path d="M31 48q2 7 11 6" fill="none" stroke="#334155" strokeWidth="2" strokeLinecap="round" />
      </g>
      <path
        className="rp-wave"
        d="M72 34q4 6 0 12"
        fill="none"
        stroke="#2b3bd1"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        className="rp-wave rp-delay-3"
        d="M78 30q6 10 0 20"
        fill="none"
        stroke="#2b3bd1"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </Frame>
  );
}

/** Mechanic: the wrench gives a quick turn. */
export function MechanicPersona(props: PersonaProps) {
  return (
    <Frame {...props}>
      <circle cx="48" cy="48" r="46" fill="#eef0ff" />
      <g className="rp-bob">
        <path d="M18 92c2-18 14-26 30-26s28 8 30 26z" fill="#1e3a8a" />
        <path d="M38 70v22M58 70v22" stroke="#3b82f6" strokeWidth="3" />
        <rect x="42" y="54" width="12" height="12" rx="4" fill="#e0ac84" />
        <circle cx="48" cy="40" r="16" fill="#e0ac84" />
        <path d="M31 36c0-10 8-15 17-15s17 5 17 15z" fill="#f97316" />
        <rect x="44" y="33" width="26" height="5" rx="2.5" fill="#ea580c" />
        <ellipse className="rp-eye rp-e4" cx="42" cy="43" rx="1.8" ry="2.2" fill="#1e293b" />
        <ellipse className="rp-eye rp-e4" cx="54" cy="43" rx="1.8" ry="2.2" fill="#1e293b" />
        <path d="M44 50q4 3 8 0" stroke="#8a4b2a" strokeWidth="1.8" fill="none" strokeLinecap="round" />
        <path d="M37 52h4" stroke="#64748b" strokeWidth="2" strokeLinecap="round" opacity=".5" />
      </g>
      <g className="rp-turn">
        <path
          d="M80 60a7 7 0 0 0-9.4 8.8L62 77.4a3 3 0 0 0 4.2 4.2l8.6-8.6A7 7 0 0 0 83.6 63.6l-4 4-3.4-.6-.6-3.4z"
          fill="#64748b"
        />
      </g>
    </Frame>
  );
}

/** Customer: approves on the phone; the approved badge pops. */
export function CustomerPersona(props: PersonaProps) {
  return (
    <Frame {...props}>
      <circle cx="48" cy="48" r="46" fill="#f0fdfa" />
      <g className="rp-bob">
        <path d="M18 92c2-18 14-26 30-26s28 8 30 26z" fill="#a855f7" />
        <rect x="42" y="54" width="12" height="12" rx="4" fill="#f5d0b5" />
        <circle cx="48" cy="40" r="16" fill="#f5d0b5" />
        <path d="M31 44c-3-15 5-23 17-23s20 8 17 23l-3-8c-6-1-14-5-18-9-2 4-6 7-10 9z" fill="#d97706" />
        <path
          d="M31 44c0 8 2 14 4 18M65 44c0 8-2 14-4 18"
          stroke="#d97706"
          strokeWidth="5"
          strokeLinecap="round"
        />
        <ellipse className="rp-eye rp-e5" cx="42" cy="42" rx="1.8" ry="2.2" fill="#1e293b" />
        <ellipse className="rp-eye rp-e5" cx="54" cy="42" rx="1.8" ry="2.2" fill="#1e293b" />
        <path d="M44 49q4 3 8 0" stroke="#b45309" strokeWidth="1.8" fill="none" strokeLinecap="round" />
      </g>
      <rect x="66" y="56" width="18" height="30" rx="4" fill="#0f172a" />
      <rect x="68.5" y="60" width="13" height="20" rx="1.5" fill="#ffffff" />
      <path
        d="M71.5 70.5l2.5 2.5 5-5"
        fill="none"
        stroke="#14b8a6"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <g className="rp-pop">
        <circle cx="84" cy="56" r="6" fill="#2b3bd1" />
        <path
          d="M81.5 56.2l1.7 1.7 3.3-3.3"
          fill="none"
          stroke="#ffffff"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </g>
    </Frame>
  );
}
