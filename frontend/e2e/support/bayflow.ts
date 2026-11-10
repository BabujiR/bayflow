import {
  expect,
  request as playwrightRequest,
  test as base,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
  type TestInfo,
} from '@playwright/test';

/**
 * Shared harness for the recorded end-to-end scenarios.
 *
 * Every scenario gets a `cast`: each actor (customer, advisor, mechanic, admin) is a separate
 * browser with its own video, and a caption at the bottom of the screen says who they are and
 * what is being tested. The video reporter (video-reporter.ts) collects the videos into
 * e2e-videos/, with a player that plays all actors of a scenario side by side, in sync.
 */

export const DEMO_PASSWORD = 'Bayflow!2026';
export const NEW_PASSWORD = 'E2e-Passw0rd!';

// ---- Unique test data ------------------------------------------------------------------------

const VIN_CHARS = 'ABCDEFGHJKLMNPRSTUVWXYZ0123456789';
let counter = 0;

/** A short id that is unique across runs, e.g. "K3P9QA". */
export function uid(): string {
  counter += 1;
  return (Date.now().toString(36) + counter.toString(36)).toUpperCase().slice(-6);
}

export function randomVin(): string {
  return Array.from({ length: 17 }, () => VIN_CHARS[Math.floor(Math.random() * VIN_CHARS.length)]).join('');
}

export interface Slot {
  /** Local day, YYYY-MM-DD (the board and list filters). */
  date: string;
  /** Value for a datetime-local input. */
  local: string;
  /** For the API. */
  iso: string;
}

/** A time `daysAhead` days from today, in the browser's (and this machine's) time zone. */
export function slot(daysAhead: number, hour: number, minute = 0): Slot {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  d.setHours(hour, minute, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  return { date, local: `${date}T${pad(hour)}:${pad(minute)}`, iso: d.toISOString() };
}

/** Spreads scenarios over different days so boards stay readable. */
let dayCursor = 1 + Math.floor(Math.random() * 20);
export function nextDay(): number {
  dayCursor += 1;
  return dayCursor;
}

// ---- Login throttle ----------------------------------------------------------------------------

/**
 * auth-service allows 10 login attempts per username per minute. The demo staff users sign in
 * in almost every scenario, so space their logins out instead of tripping the limiter.
 */
const loginTimes = new Map<string, number[]>();
const MAX_LOGINS_PER_MINUTE = 8;

async function throttleLogin(username: string): Promise<void> {
  for (;;) {
    const now = Date.now();
    const recent = (loginTimes.get(username) ?? []).filter((t) => now - t < 61_000);
    if (recent.length < MAX_LOGINS_PER_MINUTE) {
      recent.push(now);
      loginTimes.set(username, recent);
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 61_000 - (now - recent[0]!) + 250));
  }
}

// ---- API client (for arranging data quickly) --------------------------------------------------

export class Api {
  constructor(
    private readonly http: APIRequestContext,
    readonly username: string,
    private readonly token: string,
  ) {}

  private async call<T>(method: 'GET' | 'POST' | 'PATCH' | 'PUT', path: string, data?: unknown): Promise<T> {
    const response = await this.http.fetch(path, {
      method,
      data,
      headers: { Authorization: `Bearer ${this.token}` },
    });
    if (!response.ok()) {
      throw new Error(`${method} ${path} → ${response.status()} ${await response.text()}`);
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  /** The raw response, for asserting on errors. */
  async raw(method: 'GET' | 'POST' | 'PATCH', path: string, data?: unknown) {
    const response = await this.http.fetch(path, {
      method,
      data,
      headers: { Authorization: `Bearer ${this.token}` },
    });
    const body = await response.text();
    let problem: { type?: string; detail?: string } = {};
    try {
      problem = JSON.parse(body);
    } catch {
      // not JSON
    }
    return { status: response.status(), code: problem.type?.replace('urn:bayflow:problem:', ''), detail: problem.detail };
  }

  get = <T = unknown>(path: string) => this.call<T>('GET', path);
  post = <T = unknown>(path: string, data?: unknown) => this.call<T>('POST', path, data);
  patch = <T = unknown>(path: string, data?: unknown) => this.call<T>('PATCH', path, data);
}

// ---- Captions --------------------------------------------------------------------------------

type StepKind = 'step' | 'edge' | 'check';

const CAPTION_SCRIPT = () => {
  const KEY = '__bf_e2e_caption';
  const render = () => {
    const read = (): string | null => {
      try {
        return sessionStorage.getItem(KEY);
      } catch {
        return null;
      }
    };
    const raw = read();
    if (!raw || !document.body) {
      return;
    }
    const { actor, text, kind } = JSON.parse(raw) as { actor: string; text: string; kind: string };
    let box = document.getElementById('__bf_e2e_caption');
    if (!box) {
      box = document.createElement('div');
      box.id = '__bf_e2e_caption';
      box.setAttribute('aria-hidden', 'true');
      const s = box.style;
      s.position = 'fixed';
      s.left = '12px';
      s.bottom = '12px';
      s.zIndex = '2147483647';
      s.maxWidth = 'min(720px, calc(100vw - 24px))';
      s.padding = '10px 14px';
      s.borderRadius = '12px';
      s.font = '600 15px/1.35 system-ui, sans-serif';
      s.color = '#fff';
      s.pointerEvents = 'none';
      s.boxShadow = '0 6px 24px rgba(0,0,0,.35)';
      document.body.appendChild(box);
    }
    box.style.background = kind === 'edge' ? '#92400e' : kind === 'check' ? '#166534' : '#0f172a';
    box.textContent = '';
    const who = document.createElement('div');
    who.textContent = (kind === 'edge' ? 'EDGE CASE · ' : kind === 'check' ? 'CHECK · ' : '') + actor;
    who.style.font = '700 12px/1.2 system-ui, sans-serif';
    who.style.letterSpacing = '.06em';
    who.style.textTransform = 'uppercase';
    who.style.opacity = '.8';
    who.style.marginBottom = '3px';
    const what = document.createElement('div');
    what.textContent = text;
    box.append(who, what);
  };
  (window as unknown as { __bfCaption: () => void }).__bfCaption = render;
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
  new MutationObserver(() => {
    if (!document.getElementById('__bf_e2e_caption')) {
      render();
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
};

// ---- Actors and the cast ------------------------------------------------------------------------

const STEP_PAUSE_MS = Number(process.env.E2E_STEP_PAUSE ?? 900);

export interface TimelineStep {
  t: number;
  actor: string;
  text: string;
  kind: StepKind;
}

export class Actor {
  constructor(
    readonly name: string,
    readonly context: BrowserContext,
    readonly page: Page,
    private readonly cast: Cast,
  ) {}

  /** Shows a caption on this actor's screen and adds it to the scenario timeline. */
  async say(text: string, kind: StepKind = 'step'): Promise<void> {
    this.cast.record(this.name, text, kind);
    await this.show(text, kind);
  }

  /** Updates the caption without adding a timeline entry. */
  async show(text: string, kind: StepKind = 'step'): Promise<void> {
    await this.page
      .evaluate(
        ({ actor, text, kind }) => {
          try {
            sessionStorage.setItem('__bf_e2e_caption', JSON.stringify({ actor, text, kind }));
          } catch {
            // ignore
          }
          (window as unknown as { __bfCaption?: () => void }).__bfCaption?.();
        },
        { actor: this.name, text, kind },
      )
      .catch(() => undefined);
    await this.page.waitForTimeout(STEP_PAUSE_MS);
  }

  edge = (text: string) => this.say(text, 'edge');
  check = (text: string) => this.say(text, 'check');

  async signIn(username: string, password = DEMO_PASSWORD): Promise<void> {
    await throttleLogin(username);
    await this.page.goto('/login');
    await this.page.getByLabel('Username').fill(username);
    await this.page.getByLabel('Password').fill(password);
    await this.page.getByRole('button', { name: 'Sign in' }).click();
    // Signed in: the account menu shows. (The landing page varies: users return to the page
    // they were sent away from.)
    await expect(this.page.getByRole('button', { name: `Account menu for ${username}` })).toBeVisible();
  }

  async signOut(): Promise<void> {
    await this.page.getByRole('button', { name: /^Account menu for/ }).click();
    await this.page.getByRole('menuitem', { name: 'Sign out' }).click();
    await expect(this.page).toHaveURL(/\/login$/);
  }

  /** Registers a new customer through the UI and completes the profile. */
  async registerCustomer(username: string, fullName: string, password = NEW_PASSWORD): Promise<void> {
    await this.page.goto('/register');
    await this.page.getByLabel('Username').fill(username);
    await this.page.getByLabel('Email').fill(`${username}@example.com`);
    await this.page.getByLabel('Password', { exact: true }).fill(password);
    await this.page.getByLabel('Confirm password').fill(password);
    await this.page.getByRole('button', { name: 'Create account' }).click();
    await expect(this.page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
    await this.page.getByLabel('Full name').fill(fullName);
    await this.page.getByRole('button', { name: 'Save profile' }).click();
    await expect(this.page.getByRole('heading', { name: `Welcome, ${username}` })).toBeVisible();
  }

  /** A toast with this text appears. */
  async expectToast(text: string | RegExp): Promise<void> {
    await expect(this.page.locator('[data-sonner-toast]').filter({ hasText: text }).first()).toBeVisible();
  }
}

export class Cast {
  private readonly startedAt = Date.now();
  private readonly actors: { actor: Actor; offsetMs: number }[] = [];
  private readonly apis: APIRequestContext[] = [];
  readonly steps: TimelineStep[] = [];

  constructor(
    private readonly browser: Browser,
    private readonly testInfo: TestInfo,
  ) {}

  record(actor: string, text: string, kind: StepKind): void {
    this.steps.push({ t: Date.now() - this.startedAt, actor, text, kind });
  }

  private get baseURL(): string {
    return (this.testInfo.project.use.baseURL as string | undefined) ?? 'http://localhost:3000';
  }

  /** A new person at their own browser, recorded on video. */
  async actor(name: string): Promise<Actor> {
    const use = this.testInfo.project.use;
    const viewport = use.viewport ?? { width: 1280, height: 800 };
    const context = await this.browser.newContext({
      baseURL: this.baseURL,
      viewport,
      isMobile: use.isMobile,
      hasTouch: use.hasTouch,
      userAgent: use.userAgent,
      deviceScaleFactor: use.deviceScaleFactor,
      locale: 'en-US',
      bypassCSP: true,
      recordVideo: { dir: this.testInfo.outputPath('videos'), size: viewport },
    });
    await context.addInitScript(CAPTION_SCRIPT);
    const page = await context.newPage();
    const actor = new Actor(name, context, page, this);
    this.actors.push({ actor, offsetMs: Date.now() - this.startedAt });
    await page.goto('/login');
    await actor.say('joins the scenario');
    return actor;
  }

  /** A signed-in API client, for arranging data without clicking through the UI. */
  async api(username: string, password = DEMO_PASSWORD): Promise<Api> {
    await throttleLogin(username);
    const http = await playwrightRequest.newContext({ baseURL: this.baseURL });
    this.apis.push(http);
    const response = await http.post('/api/v1/auth/login', { data: { username, password } });
    expect(response.ok(), `API login for ${username}`).toBeTruthy();
    const { accessToken } = (await response.json()) as { accessToken: string };
    return new Api(http, username, accessToken);
  }

  /** An unauthenticated API client. */
  async anonymousApi(): Promise<APIRequestContext> {
    const http = await playwrightRequest.newContext({ baseURL: this.baseURL });
    this.apis.push(http);
    return http;
  }

  /** A caption that is not tied to one browser (e.g. an API-level check). */
  async note(text: string, kind: StepKind = 'check'): Promise<void> {
    this.record('API', text, kind);
    await Promise.all(this.actors.map(({ actor }) => actor.show(`API · ${text}`, kind).catch(() => undefined)));
  }

  /** Closes every browser, attaches each video and the timeline to the report. */
  async finish(): Promise<void> {
    const videos: { actor: string; offsetMs: number; path: string }[] = [];
    for (const { actor, offsetMs } of this.actors) {
      const video = actor.page.video();
      await actor.context.close().catch(() => undefined);
      const path = await video?.path().catch(() => undefined);
      if (path) {
        videos.push({ actor: actor.name, offsetMs, path });
        await this.testInfo.attach(`video: ${actor.name}`, { path, contentType: 'video/webm' });
      }
    }
    await Promise.all(this.apis.map((http) => http.dispose()));
    await this.testInfo.attach('bayflow-timeline', {
      contentType: 'application/json',
      body: JSON.stringify({ videos, steps: this.steps, durationMs: Date.now() - this.startedAt }),
    });
  }
}

export const test = base.extend<{ cast: Cast }>({
  // `provide` is Playwright's fixture callback (usually named `use`; renamed so the React
  // hooks lint rule does not mistake it for React's use()).
  cast: async ({ browser }, provide, testInfo) => {
    const cast = new Cast(browser, testInfo);
    await provide(cast);
    await cast.finish();
  },
});

export { expect };

// ---- Arranging data through the API -----------------------------------------------------------

export interface SeededCustomer {
  username: string;
  password: string;
  name: string;
  api: Api;
  customerId: string;
  vehicle: { id: string; vin: string; registration: string; label: string; mileage: number };
}

/** A registered customer with a profile and one vehicle, created through the API. */
export async function seedCustomer(cast: Cast, options: { mileage?: number } = {}): Promise<SeededCustomer> {
  const id = uid();
  const username = `e2e-${id.toLowerCase()}`;
  const name = `E2E Customer ${id}`;
  const anonymous = await cast.anonymousApi();
  const registered = await anonymous.post('/api/v1/auth/register', {
    data: { username, email: `${username}@example.com`, password: NEW_PASSWORD },
  });
  expect(registered.ok(), 'register customer').toBeTruthy();
  const api = await cast.api(username, NEW_PASSWORD);
  const customer = await api.post<{ id: string }>('/api/v1/customers/me', { name, email: `${username}@example.com` });
  const mileage = options.mileage ?? 20_000;
  const registration = `E2E ${id}`;
  const vin = randomVin();
  const vehicle = await api.post<{ id: string }>('/api/v1/vehicles', {
    vin,
    registration,
    make: 'Honda',
    model: 'Civic',
    year: 2020,
    mileage,
  });
  return {
    username,
    password: NEW_PASSWORD,
    name,
    api,
    customerId: customer.id,
    vehicle: { id: vehicle.id, vin, registration, label: `${registration} — 2020 Honda Civic`, mileage },
  };
}

export async function book(
  api: Api,
  vehicleId: string,
  at: Slot,
  options: { durationMinutes?: number; serviceType?: string; notes?: string } = {},
): Promise<{ id: string }> {
  return api.post<{ id: string }>('/api/v1/appointments', {
    vehicleId,
    scheduledAt: at.iso,
    durationMinutes: options.durationMinutes ?? 60,
    serviceType: options.serviceType ?? 'ROUTINE_SERVICE',
    notes: options.notes,
  });
}

/** Confirms, marks arrived and checks in; returns the job card id once workshop-service opened it. */
export async function checkInViaApi(advisor: Api, appointmentId: string, mileage: number): Promise<string> {
  await advisor.post(`/api/v1/appointments/${appointmentId}/confirm`);
  await advisor.post(`/api/v1/appointments/${appointmentId}/arrive`);
  await advisor.post(`/api/v1/appointments/${appointmentId}/check-in`, { mileage });
  let jobCardId: string | undefined;
  await expect
    .poll(
      async () => {
        const appointment = await advisor.get<{ jobCardId?: string | null }>(`/api/v1/appointments/${appointmentId}`);
        jobCardId = appointment.jobCardId ?? undefined;
        return jobCardId;
      },
      { timeout: 60_000, message: 'job card opened after check-in' },
    )
    .toBeTruthy();
  return jobCardId!;
}

/** Adds a finding and the given recommendations, then sends the card to the customer. */
export async function inspectViaApi(
  advisor: Api,
  jobCardId: string,
  items: { description: string; parts: number; hours: number }[],
): Promise<{ id: string; description: string }[]> {
  await advisor.post(`/api/v1/job-cards/${jobCardId}/findings`, { description: 'Inspection notes', severity: 'MEDIUM' });
  let card: { recommendations: { id: string; description: string }[] } = { recommendations: [] };
  for (const item of items) {
    card = await advisor.post<typeof card>(`/api/v1/job-cards/${jobCardId}/recommendations`, {
      description: item.description,
      estimatedPartsCost: item.parts,
      estimatedLabourHours: item.hours,
    });
  }
  await advisor.post(`/api/v1/job-cards/${jobCardId}/submit-for-approval`);
  return card.recommendations;
}

// ---- UI helpers ------------------------------------------------------------------------------------

/** The appointments list, filtered to one day, and the row for a vehicle. */
export async function appointmentRow(actor: Actor, date: string, registration: string) {
  const { page } = actor;
  if (!/\/appointments$/.test(new URL(page.url()).pathname)) {
    await page.goto('/appointments');
  }
  await page.getByLabel('Day').fill(date);
  const row = page.getByRole('row', { name: new RegExp(registration) }).first();
  await expect(row).toBeVisible();
  return row;
}

/** Follows the row's "Job card" link; reloads while workshop-service is still opening it. */
export async function openJobCardFromRow(actor: Actor, date: string, registration: string, timeout = 90_000) {
  const { page } = actor;
  await expect(async () => {
    const row = await appointmentRow(actor, date, registration);
    const link = row.getByRole('link', { name: 'Job card' });
    if (!(await link.isVisible())) {
      await page.reload();
    }
    await expect((await appointmentRow(actor, date, registration)).getByRole('link', { name: 'Job card' })).toBeVisible({
      timeout: 3_000,
    });
  }).toPass({ timeout, intervals: [2_000, 5_000] });
  await (await appointmentRow(actor, date, registration)).getByRole('link', { name: 'Job card' }).click();
  await expect(page.getByRole('heading', { name: new RegExp(`Job card · ${registration}`) })).toBeVisible();
}

/** The job card page heading, which carries the status badge. */
export function jobCardHeading(actor: Actor) {
  return actor.page.getByRole('heading', { name: /Job card ·/ });
}
