import { useState } from 'react';
import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { PRODUCT_NAME } from '@/lib/brand';
import { BASE_DOMAIN, browser, workshopUrl } from '@/lib/site';
import { SLUG_PATTERN } from '@/lib/slug';

const FEATURES = [
  {
    title: 'Online booking',
    text: 'Customers book, reschedule and cancel themselves; double bookings are refused.',
  },
  { title: 'Job cards', text: 'Inspection findings, estimates and customer approval, line by line.' },
  { title: 'Your own address', text: `Every workshop gets its own site at your-workshop.${BASE_DOMAIN}.` },
];

/** The public landing page: what the product is, sign up, or go to an existing workshop. */
export function LandingPage() {
  const [slug, setSlug] = useState('');
  const go = slug.trim().toLowerCase();
  const validGo = SLUG_PATTERN.test(go);

  return (
    <div className="grid gap-10 pt-10">
      <section className="grid gap-4 text-center">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
          Run your auto workshop on {PRODUCT_NAME}
        </h1>
        <p className="mx-auto max-w-2xl text-muted-foreground">
          Bookings, check-in, inspections, customer approvals and service history in one place, for your front
          desk, your mechanics and your customers.
        </p>
        <div className="flex justify-center">
          <Button asChild size="lg">
            <Link to="/signup">Set up your workshop</Link>
          </Button>
        </div>
      </section>

      <section aria-label="Features" className="grid gap-4 sm:grid-cols-3">
        {FEATURES.map((f) => (
          <Card key={f.title}>
            <CardHeader>
              <CardTitle className="text-base">{f.title}</CardTitle>
              <CardDescription>{f.text}</CardDescription>
            </CardHeader>
          </Card>
        ))}
      </section>

      <Card className="mx-auto w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-base">Already on {PRODUCT_NAME}?</CardTitle>
          <CardDescription>Go to your workshop's address to sign in.</CardDescription>
        </CardHeader>
        <CardContent>
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
            <div className="flex-1">
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
        </CardContent>
      </Card>
    </div>
  );
}
