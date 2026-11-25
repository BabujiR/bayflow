import { Button } from '@/components/ui/button';
import { PRODUCT_NAME } from '@/lib/brand';

/**
 * "Switching to Ramplight": the migration section reviewed on the design canvas. NOT on the
 * landing page yet: add `<SwitchingSection />` to LandingPage.tsx (after "Made for the whole
 * shop", before the final call to action) once every promise below is true on day one:
 *
 * - "we bring over your customers, vehicles and history": there is no import tool yet, so
 *   someone at Ramplight does it by hand from the shop's export (or build an import first);
 * - "month to month, cancel any time": matches the real pricing and terms;
 * - "take your data with you": owners can export more than invoices (customers, vehicles).
 *
 * The motion lives in index.css (.sw-*) and stops for people who ask for reduced motion.
 */
export function SwitchingSection() {
  return (
    <section
      aria-labelledby="switching-heading"
      className="grid items-center gap-12 rounded-2xl border bg-card p-6 sm:p-10 lg:grid-cols-2"
    >
      <div className="grid gap-5">
        <p className="text-sm font-semibold uppercase tracking-[0.12em] text-primary">
          Switching to {PRODUCT_NAME}
        </p>
        <h2 id="switching-heading" className="text-3xl font-bold tracking-tight sm:text-4xl">
          Moving over is
          <br />
          <span className="text-primary">the easy part.</span>
        </h2>
        <p className="max-w-md text-lg text-muted-foreground">
          Bring your customers, vehicles and service history with you. We set it up with you, so your team
          keeps working while you switch.
        </p>
        <MovingData />
      </div>
      <div>
        <ul className="grid">
          {POINTS.map((point, index) => (
            <li key={point.title} className="flex gap-4 border-b py-6">
              <Tick delay={index} />
              <div>
                <h3 className="text-lg font-semibold">{point.title}</h3>
                <p className="mt-1 text-muted-foreground">{point.text}</p>
              </div>
            </li>
          ))}
        </ul>
        <Button asChild size="lg" className="mt-6">
          <a href="#demo">Talk to us about switching</a>
        </Button>
      </div>
    </section>
  );
}

const POINTS = [
  {
    title: 'Up and running in days',
    text: 'Your workshop address, settings and team are ready on day one.',
  },
  {
    title: 'A real person helps you move',
    text: 'Send us an export from your current system and we bring over your customers, vehicles and history, then walk your team through the first day.',
  },
  {
    title: 'Month to month',
    text: 'No long contract. Cancel any time, and take your data with you.',
  },
];

/** A check that draws itself in; the three run one after another. */
function Tick({ delay }: { delay: number }) {
  return (
    <svg viewBox="0 0 40 40" aria-hidden="true" className="size-10 shrink-0 text-primary">
      <circle className={`sw-ring sw-d${delay}`} cx="20" cy="20" r="18" fill="currentColor" opacity={0.1} />
      <path
        className={`sw-tick sw-d${delay}`}
        d="M12 20.5l5.5 5.5 11-11"
        fill="none"
        stroke="currentColor"
        strokeWidth={3}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Data chips travelling from "Your old system" into Ramplight. */
function MovingData() {
  return (
    <svg
      viewBox="0 0 500 220"
      role="img"
      aria-label={`Your data moving from your old system into ${PRODUCT_NAME}`}
      className="h-auto w-full max-w-md text-primary"
    >
      <path
        d="M120 110 C 200 40, 300 180, 380 110"
        fill="none"
        stroke="currentColor"
        strokeOpacity={0.3}
        strokeWidth={2.5}
        strokeDasharray="6 8"
      />
      <rect x="20" y="66" width="104" height="88" rx="16" className="fill-muted stroke-border" />
      <path
        d="M44 92h56M44 110h56M44 128h36"
        className="stroke-muted-foreground/50"
        strokeWidth={5}
        strokeLinecap="round"
      />
      <text x="72" y="182" textAnchor="middle" fontSize="13" className="fill-muted-foreground">
        Your old system
      </text>
      <rect x="376" y="62" width="104" height="96" rx="20" fill="currentColor" />
      <text x="428" y="124" textAnchor="middle" fontSize="44" fontWeight="800" fill="#ffffff">
        R
      </text>
      <circle cx="458" cy="80" r="6" fill="#5eead4" />
      <text x="428" y="186" textAnchor="middle" fontSize="13" fontWeight="600" fill="currentColor">
        {PRODUCT_NAME}
      </text>
      <rect className="sw-chip" x="-11" y="-8" width="22" height="16" rx="4" fill="#14b8a6" />
      <rect className="sw-chip sw-d1" x="-11" y="-8" width="22" height="16" rx="4" fill="currentColor" />
      <rect className="sw-chip sw-d2" x="-11" y="-8" width="22" height="16" rx="4" fill="#5eead4" />
    </svg>
  );
}
