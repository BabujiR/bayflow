import type { SVGProps } from 'react';

/**
 * The landing page's feature icons: line icons (24px grid) with a short loop that shows what
 * each feature does. The motion lives in index.css (.rl-anim-*) and stops for people who ask
 * their device for reduced motion. Decorative: the card's title says what it is.
 */
type IconProps = SVGProps<SVGSVGElement>;

const ACCENT = '#14b8a6';

function Svg({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {children}
    </svg>
  );
}

/** Online booking: the calendar's page flips, then a tick draws in. */
export function BookingIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="3" y="4" width="18" height="17" rx="2.5" />
      <path d="M8 2.5v3M16 2.5v3" />
      <path className="rl-anim-flip" d="M3 9h18" />
      <path className="rl-anim-draw" d="M8.5 15l2.2 2.2 4.8-4.8" stroke={ACCENT} />
    </Svg>
  );
}

/** Today's board: job cards slide along the board. */
export function BoardMotionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="3" y="3" width="18" height="18" rx="2.5" />
      <path d="M12 3v18" />
      <rect className="rl-anim-slide" x="5.5" y="6" width="4" height="3" rx="1" fill={ACCENT} stroke="none" />
      <rect
        className="rl-anim-slide rl-delay-1"
        x="5.5"
        y="11"
        width="4"
        height="3"
        rx="1"
        fill="currentColor"
        stroke="none"
        opacity={0.4}
      />
      <rect x="14.5" y="6" width="4" height="3" rx="1" fill="currentColor" stroke="none" opacity={0.4} />
    </Svg>
  );
}

/** Inspections & approvals: a tick draws itself on the checklist. */
export function InspectionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="5" y="4" width="14" height="17" rx="2" />
      <rect x="9" y="2.5" width="6" height="3" rx="1" />
      <path className="rl-anim-draw" d="M8.5 11l1.6 1.6 3-3" stroke={ACCENT} />
      <path d="M14.5 11.5h1.5M8.5 16.5h7" />
    </Svg>
  );
}

/** Canned jobs: the wrench gives a quick turn. */
export function WrenchMotionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <g className="rl-anim-turn">
        <path d="M14.7 6.3a4 4 0 0 0-5.4 5.2L3.5 17.3a1.8 1.8 0 0 0 2.5 2.5l5.8-5.8a4 4 0 0 0 5.2-5.4l-2.5 2.5-2.1-.4-.4-2.1z" />
      </g>
      <circle cx="18.5" cy="18.5" r="1.6" fill={ACCENT} stroke="none" />
    </Svg>
  );
}

/** Invoices & payments: the receipt prints. */
export function ReceiptMotionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 3.5h16" />
      <g className="rl-anim-print">
        <path d="M6 3.5v17l2-1.4 2 1.4 2-1.4 2 1.4 2-1.4 2 1.4v-17" />
        <path d="M9 8.5h6M9 12h6" />
        <path d="M9 15.5h3" stroke={ACCENT} />
      </g>
    </Svg>
  );
}

/** Maintenance reminders: the bell rings. */
export function BellMotionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <g className="rl-anim-ring">
        <path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" />
        <path d="M12 3v2" />
      </g>
      <path d="M10 20.5a2 2 0 0 0 4 0" />
      <circle cx="18.5" cy="5.5" r="2.2" fill="#f97316" stroke="none" />
    </Svg>
  );
}

/** Dashboard & reports: the bars grow one after another. */
export function ChartMotionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3.5 20.5h17" />
      <rect
        className="rl-anim-grow"
        x="5.5"
        y="12"
        width="3"
        height="8"
        rx="1"
        fill="currentColor"
        stroke="none"
        opacity={0.4}
      />
      <rect
        className="rl-anim-grow rl-delay-1"
        x="10.5"
        y="8"
        width="3"
        height="12"
        rx="1"
        fill="currentColor"
        stroke="none"
        opacity={0.65}
      />
      <rect
        className="rl-anim-grow rl-delay-2"
        x="15.5"
        y="4.5"
        width="3"
        height="15.5"
        rx="1"
        fill={ACCENT}
        stroke="none"
      />
    </Svg>
  );
}

/** One shop or many: the map pin bounces over a ripple. */
export function PinMotionIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <ellipse className="rl-anim-pulse" cx="12" cy="21" rx="4" ry="1.2" stroke={ACCENT} />
      <g className="rl-anim-bounce">
        <path d="M12 19s6-5.3 6-10a6 6 0 0 0-12 0c0 4.7 6 10 6 10z" />
        <circle cx="12" cy="9" r="2.2" fill={ACCENT} stroke="none" />
      </g>
    </Svg>
  );
}
