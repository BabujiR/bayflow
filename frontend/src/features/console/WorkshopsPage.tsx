import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';

import { consoleApi, tenantsApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { ConsoleWorkshop } from '@/api/types';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, timeZones } from '@/lib/format';
import { BASE_DOMAIN, workshopUrl } from '@/lib/site';
import { SLUG_PATTERN, suggestSlug } from '@/lib/slug';
import { useAction } from '@/lib/use-action';

const KEY = ['console', 'workshops'] as const;
type Kind = '' | 'customers' | 'test';

/** Console → Workshops: every workshop, suspend / reactivate, and test workshops. */
export function WorkshopsPage() {
  const [status, setStatus] = useState<'' | ConsoleWorkshop['status']>('');
  const [kind, setKind] = useState<Kind>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [confirming, setConfirming] = useState<ConsoleWorkshop | null>(null);
  const list = useQuery({
    queryKey: [...KEY, status, kind, q.trim(), page],
    queryFn: () =>
      consoleApi.workshops({
        status: status || undefined,
        test: kind === '' ? undefined : kind === 'test',
        q: q.trim() || undefined,
        page,
        size: 25,
      }),
  });
  return (
    <div className="grid gap-6">
      <PageHeader
        title="Workshops"
        description="Every workshop on Ramplight. Suspending one stops all sign-ins there and keeps its data."
        actions={
          <Button variant="outline" onClick={() => setCreating(true)}>
            Create test workshop
          </Button>
        }
      />
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Status">
          <Select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as '' | ConsoleWorkshop['status']);
              setPage(0);
            }}
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
          </Select>
        </Field>
        <Field label="Kind">
          <Select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as Kind);
              setPage(0);
            }}
          >
            <option value="">All</option>
            <option value="customers">Customers</option>
            <option value="test">Test</option>
          </Select>
        </Field>
        <div className="w-full sm:w-72">
          <Field label="Search">
            <Input
              placeholder="Name or address"
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                setPage(0);
              }}
            />
          </Field>
        </div>
      </div>
      <QueryState query={list}>
        {(data) =>
          data.content.length === 0 ? (
            <Card>
              <CardContent className="pt-6 text-sm text-muted-foreground">No workshops here.</CardContent>
            </Card>
          ) : (
            <>
              <Card>
                <CardContent className="overflow-x-auto pt-6">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Workshop</TableHead>
                        <TableHead>Owner</TableHead>
                        <TableHead className="text-right">Staff</TableHead>
                        <TableHead className="text-right">Customers</TableHead>
                        <TableHead className="text-right">Locations</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>
                          <span className="sr-only">Actions</span>
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.content.map((w) => (
                        <TableRow key={w.id}>
                          <TableCell>
                            <div className="flex items-center gap-2 font-medium">
                              {w.name}
                              {w.test && <Badge variant="warning">Test</Badge>}
                            </div>
                            <a
                              className="text-xs text-primary underline"
                              href={workshopUrl(w.slug, '/login')}
                              target="_blank"
                              rel="noreferrer"
                            >
                              {w.slug}.{BASE_DOMAIN}
                            </a>
                            <div className="text-xs text-muted-foreground">
                              Since {formatDate(w.createdAt)}
                              {w.invitedEmail ? ' · by invite' : ''}
                            </div>
                          </TableCell>
                          <TableCell className="text-sm">
                            <div>{w.ownerUsername ?? '—'}</div>
                            {w.ownerEmail && <div className="text-muted-foreground">{w.ownerEmail}</div>}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{w.staff}</TableCell>
                          <TableCell className="text-right tabular-nums">{w.customers}</TableCell>
                          <TableCell className="text-right tabular-nums">{w.locations}</TableCell>
                          <TableCell>
                            <Badge variant={w.status === 'ACTIVE' ? 'success' : 'destructive'}>
                              {w.status === 'ACTIVE' ? 'Active' : 'Suspended'}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            {w.status === 'ACTIVE' ? (
                              <Button
                                size="sm"
                                variant="outline"
                                aria-label={`Suspend ${w.name}`}
                                onClick={() => setConfirming(w)}
                              >
                                Suspend
                              </Button>
                            ) : (
                              <ReactivateButton workshop={w} />
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
              <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
            </>
          )
        }
      </QueryState>
      {creating && <TestWorkshopDialog onClose={() => setCreating(false)} />}
      {confirming && <SuspendDialog workshop={confirming} onClose={() => setConfirming(null)} />}
    </div>
  );
}

function ReactivateButton({ workshop }: { workshop: ConsoleWorkshop }) {
  const reactivate = useAction(() => consoleApi.reactivateWorkshop(workshop.id), {
    success: `${workshop.name} is active again`,
    invalidate: [[...KEY]],
  });
  return (
    <Button
      size="sm"
      variant="outline"
      aria-label={`Reactivate ${workshop.name}`}
      disabled={reactivate.isPending}
      onClick={() => reactivate.mutate(undefined)}
    >
      Reactivate
    </Button>
  );
}

function SuspendDialog({ workshop, onClose }: { workshop: ConsoleWorkshop; onClose: () => void }) {
  const suspend = useAction(() => consoleApi.suspendWorkshop(workshop.id), {
    success: `${workshop.name} suspended`,
    invalidate: [[...KEY]],
    onSuccess: onClose,
  });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={`Suspend ${workshop.name}?`}>
        <p className="text-sm text-muted-foreground">
          Nobody can sign in at {workshop.slug}.{BASE_DOMAIN} (staff or customers) until you reactivate it.
          Its data is kept, and people already signed in are signed out within 15 minutes.
        </p>
        <div className="mt-4 flex gap-2">
          <Button
            variant="destructive"
            disabled={suspend.isPending}
            onClick={() => suspend.mutate(undefined)}
          >
            {suspend.isPending ? 'Suspending…' : 'Suspend workshop'}
          </Button>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'AUD', 'NZD', 'CAD'];

/** A workshop for internal testing, without an invite (labelled Test everywhere). */
function TestWorkshopDialog({ onClose }: { onClose: () => void }) {
  const zones = useMemo(() => timeZones(), []);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugEdited, setSlugEdited] = useState(false);
  const [currency, setCurrency] = useState('USD');
  const [timeZone, setTimeZone] = useState('America/New_York');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [slugError, setSlugError] = useState<string | null>(null);

  useEffect(() => {
    if (!slugEdited) {
      setSlug(suggestSlug(name));
    }
  }, [name, slugEdited]);

  const address = slug.trim().toLowerCase();
  const availability = useQuery({
    queryKey: ['slug-availability', address],
    queryFn: () => tenantsApi.availability(address),
    enabled: SLUG_PATTERN.test(address),
    staleTime: 10_000,
  });
  const taken = availability.data?.slug === address && !availability.data.available;
  const valid =
    name.trim() !== '' &&
    SLUG_PATTERN.test(address) &&
    !taken &&
    /^[A-Za-z0-9._-]{3,50}$/.test(username.trim()) &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) &&
    password.length >= 8;

  const create = useAction(
    () =>
      consoleApi.createTestWorkshop({
        workshopName: name.trim(),
        slug: address,
        currency,
        timeZone,
        adminUsername: username.trim(),
        adminEmail: email.trim(),
        adminPassword: password,
      }),
    {
      success: (w) => `Test workshop ${w.slug} created`,
      invalidate: [[...KEY]],
      onSuccess: onClose,
      onError: (error) => {
        if (
          error instanceof ApiError &&
          ['slug-taken', 'slug-reserved', 'invalid-slug'].includes(error.code)
        ) {
          setSlugError(error.message);
          return true;
        }
        return false;
      },
    },
  );

  const slugHint = !SLUG_PATTERN.test(address)
    ? '3-30 lower-case letters, digits and hyphens'
    : taken
      ? undefined
      : `${address}.${BASE_DOMAIN}`;
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        title="Create test workshop"
        description="For internal testing: no invite needed, and it's labelled Test in the console."
      >
        <form
          noValidate
          aria-label="Create test workshop"
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (valid) {
              create.mutate(undefined);
            }
          }}
        >
          <Field label="Workshop name">
            <Input autoFocus value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field
            label="Workshop address"
            hint={slugHint}
            error={slugError ?? (taken ? `${address} is ${availability.data?.reason ?? 'taken'}` : undefined)}
          >
            <Input
              autoCapitalize="none"
              spellCheck={false}
              value={slug}
              onChange={(e) => {
                setSlugEdited(true);
                setSlugError(null);
                setSlug(e.target.value);
              }}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Currency">
              <Select value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Time zone">
              <Select value={timeZone} onChange={(e) => setTimeZone(e.target.value)}>
                {zones.map((zone) => (
                  <option key={zone} value={zone}>
                    {zone.replaceAll('_', ' ')}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="Admin username">
            <Input autoComplete="off" value={username} onChange={(e) => setUsername(e.target.value)} />
          </Field>
          <Field label="Admin email">
            <Input type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Admin password" hint="At least 8 characters.">
            <Input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          <Button type="submit" className="justify-self-start" disabled={!valid || create.isPending}>
            {create.isPending ? 'Creating…' : 'Create test workshop'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
