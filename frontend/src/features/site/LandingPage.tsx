import { useState, type ComponentType, type SVGProps } from 'react';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import {
  BellIcon,
  BoardIcon,
  CalendarIcon,
  ChartIcon,
  JobCardIcon,
  MapPinIcon,
  ReceiptIcon,
  WrenchIcon,
} from '@/components/ui/icons';
import { Input } from '@/components/ui/input';
import { PRODUCT_NAME } from '@/lib/brand';
import { BASE_DOMAIN, browser, workshopUrl } from '@/lib/site';
import { SLUG_PATTERN } from '@/lib/slug';

import { DemoRequestForm } from './DemoRequestForm';

type IconType = ComponentType<SVGProps<SVGSVGElement>>;

const BENEFITS = [
  { title: 'Faster at the counter', text: 'Canned jobs, one-click estimates and a live board of the day.' },
  { title: 'More approved work', text: 'Customers see each recommendation and approve it from their phone.' },
  { title: 'Customers come back', text: 'Maintenance reminders tell them when their car is due again.' },
];

const FEATURES: { icon: IconType; title: string; text: string }[] = [
  {
    icon: CalendarIcon,
    title: 'Online booking',
    text: 'Customers book, reschedule and cancel on your own site; double bookings are refused.',
  },
  {
    icon: BoardIcon,
    title: "Today's board",
    text: 'Confirm, check in and track every car of the day from one screen at the front desk.',
  },
  {
    icon: JobCardIcon,
    title: 'Inspections & approvals',
    text: 'Findings with severity, recommended work with estimates, and approval line by line.',
  },
  {
    icon: WrenchIcon,
    title: 'Canned jobs',
    text: 'Your everyday work with its hours and usual parts, added to an estimate in one click.',
  },
  {
    icon: ReceiptIcon,
    title: 'Invoices & payments',
    text: 'Tax and shop fees worked out, payments recorded, invoices emailed and sent to QuickBooks.',
  },
  {
    icon: BellIcon,
    title: 'Maintenance reminders',
    text: 'Your own service intervals; customers are told when their vehicle is due again.',
  },
  {
    icon: ChartIcon,
    title: 'Dashboard & reports',
    text: 'Sales, collected and unpaid, technicians, advisors, parts and tax, by day or month.',
  },
  {
    icon: MapPinIcon,
    title: 'One shop or many',
    text: 'Add locations as you grow; staff see their own, owners see the whole group.',
  },
];

const GROUP_POINTS = [
  'Owners and managers see sales, unpaid invoices and workload per location or for the whole group.',
  'Advisors and mechanics see only the locations they work at.',
  'Each location keeps its own labour rate, tax rates, fees and time zone.',
  'Customers book at the location of their choice; their history follows them.',
  'One brand, one address, one invoice numbering across the group.',
];

const STEPS = [
  {
    title: 'Set up your workshop',
    text: `Choose your address (your-workshop.${BASE_DOMAIN}), currency and time zone. It takes two minutes.`,
  },
  {
    title: 'Add your team and branding',
    text: 'Invite advisors and mechanics, upload your logo and colours.',
  },
  {
    title: 'Open the doors',
    text: 'Customers book online or at the counter, and every job flows from check-in to invoice.',
  },
];

const ROLES = [
  { who: 'Owners', text: 'See the money and the workload at a glance, across every location.' },
  { who: 'Service advisors', text: 'Book, check in, estimate and invoice without paper or retyping.' },
  { who: 'Mechanics', text: 'Their jobs, the approved work, and parts and labour recorded as they go.' },
  { who: 'Customers', text: 'Book, approve work, follow their car and see invoices online.' },
];

function Check() {
  return (
    <svg aria-hidden viewBox="0 0 20 20" className="mt-0.5 size-5 shrink-0">
      <circle cx="10" cy="10" r="10" className="fill-emerald-500" />
      <path d="M6 10.5l2.5 2.5L14 7.5" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

/** The public landing page: what the product is, sign up, or go to an existing workshop. */
export function LandingPage() {
  return (
    <div className="grid gap-20 pt-8 sm:pt-14">
      {/* ---- hero ---- */}
      <section className="grid items-center gap-10 lg:grid-cols-[1.15fr_1fr]">
        <div className="grid gap-6">
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-primary">
            For independent shops and multi-location groups
          </p>
          <h1 className="text-4xl font-bold leading-tight tracking-tight sm:text-5xl">
            Every bay, every job,
            <br />
            <span className="text-primary">one clear view.</span>
          </h1>
          <p className="max-w-xl text-lg text-muted-foreground">
            {PRODUCT_NAME} runs your shop from the front counter to the back bay: bookings, inspections,
            customer approvals, invoices and reminders, in one place. One shop or twenty.
          </p>
          <ul className="grid gap-3">
            {BENEFITS.map((b) => (
              <li key={b.title} className="flex gap-3">
                <Check />
                <span>
                  <strong className="font-semibold">{b.title}:</strong>{' '}
                  <span className="text-muted-foreground">{b.text}</span>
                </span>
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link to="/signup">Set up your workshop</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <a href="#demo">Request a demo</a>
            </Button>
          </div>
        </div>
        <DemoRequestForm />
      </section>

      <WorkshopStrip />

      {/* ---- features ---- */}
      <section id="features" aria-labelledby="features-heading" className="grid scroll-mt-8 gap-8">
        <div className="grid gap-2 text-center">
          <h2 id="features-heading" className="text-3xl font-bold tracking-tight">
            Everything the shop needs, nothing it doesn&apos;t
          </h2>
          <p className="mx-auto max-w-2xl text-muted-foreground">
            Built around how a workshop really runs, so your team learns it in an afternoon.
          </p>
        </div>
        <div aria-label="Features" role="list" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <Card key={title} role="listitem" className="transition-shadow hover:shadow-md">
              <CardHeader className="gap-3">
                <span className="grid size-10 place-items-center rounded-lg bg-primary/10 text-primary">
                  <Icon className="size-5" />
                </span>
                <CardTitle className="text-base">{title}</CardTitle>
                <CardDescription>{text}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </section>

      {/* ---- groups ---- */}
      <section
        aria-labelledby="groups-heading"
        className="grid items-center gap-8 rounded-2xl border bg-card p-6 sm:p-10 lg:grid-cols-2"
      >
        <div className="grid gap-3">
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-primary">Multi-location</p>
          <h2 id="groups-heading" className="text-3xl font-bold tracking-tight">
            Grow from one bay to a group of shops
          </h2>
          <p className="text-muted-foreground">
            Run every location from one account: each with its own hours, labour rate, taxes and team, all
            under your brand and your address.
          </p>
        </div>
        <ul className="grid gap-3">
          {GROUP_POINTS.map((point) => (
            <li key={point} className="flex gap-3">
              <Check />
              <span>{point}</span>
            </li>
          ))}
        </ul>
      </section>

      {/* ---- how it works ---- */}
      <section aria-labelledby="how-heading" className="grid gap-8">
        <h2 id="how-heading" className="text-center text-3xl font-bold tracking-tight">
          Up and running today
        </h2>
        <ol className="grid gap-6 sm:grid-cols-3">
          {STEPS.map((step, index) => (
            <li key={step.title} className="grid content-start gap-2">
              <span className="grid size-9 place-items-center rounded-full bg-primary font-semibold text-primary-foreground">
                {index + 1}
              </span>
              <h3 className="font-semibold">{step.title}</h3>
              <p className="text-sm text-muted-foreground">{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* ---- who it's for ---- */}
      <section aria-labelledby="roles-heading" className="grid gap-8">
        <h2 id="roles-heading" className="text-center text-3xl font-bold tracking-tight">
          Made for the whole shop
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {ROLES.map((r) => (
            <div key={r.who} className="rounded-xl border bg-card p-5">
              <h3 className="font-semibold">{r.who}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{r.text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ---- final call to action ---- */}
      <section className="grid gap-4 rounded-2xl bg-primary px-6 py-12 text-center text-primary-foreground">
        <h2 className="text-3xl font-bold tracking-tight">Ready to run a smoother shop?</h2>
        <p className="mx-auto max-w-xl opacity-90">
          Set up your workshop in two minutes. Your address, your branding, your team.
        </p>
        <div className="flex justify-center">
          <Button asChild size="lg" variant="secondary">
            <Link to="/signup">Get started</Link>
          </Button>
        </div>
      </section>
    </div>
  );
}

/** Below the hero: go to an existing workshop to sign in. */
function WorkshopStrip() {
  const [slug, setSlug] = useState('');
  const go = slug.trim().toLowerCase();
  const validGo = SLUG_PATTERN.test(go);
  return (
    <section
      aria-label="Already on Ramplight"
      className="-mt-8 grid items-end gap-4 rounded-xl border bg-card p-5 sm:grid-cols-[1fr_auto]"
    >
      <div className="sm:pb-6">
        <h2 className="font-semibold">Already on {PRODUCT_NAME}?</h2>
        <p className="text-sm text-muted-foreground">Go to your workshop&apos;s address to sign in.</p>
      </div>
      <form
        aria-label="Go to your workshop"
        className="flex items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          // Only a valid workshop address is ever turned into a URL.
          if (SLUG_PATTERN.test(go)) {
            browser.go(workshopUrl(go, '/login'));
          }
        }}
      >
        <div className="w-full sm:w-72">
          <Field
            label="Workshop address"
            hint={`${validGo ? go : 'your-workshop'}.${BASE_DOMAIN}`}
            error={go && !validGo ? 'Letters, numbers and hyphens only, e.g. acme-auto' : undefined}
          >
            <Input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="your-workshop" />
          </Field>
        </div>
        <Button type="submit" variant="outline" className="mb-6" disabled={!validGo}>
          Go
        </Button>
      </form>
    </section>
  );
}
