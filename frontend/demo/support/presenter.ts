import { copyFile, mkdir, writeFile } from 'node:fs/promises';
import { basename, join } from 'node:path';

import { expect, test as base, type Locator, type Page } from '@playwright/test';

import '../../e2e/support/localhost-dns';

import { createNarrator, joinWithVideo, narrationTrack, type Clip, type Cue, type Narrator } from './narrator';

/**
 * Harness for the recorded product demo (see demo/README.md). One browser page per video;
 * a "presenter" moves a visible cursor to each control before using it, types at a human
 * pace and shows captions and chapter cards, so the video explains itself without a voiceover.
 */

/** Pace multiplier: 1 = normal, 1.3 = slower, 0.5 = quick test run. */
export const PACE = Number(process.env.DEMO_PACE ?? 1);
const scaled = (ms: number) => Math.round(ms * PACE);

/** The public site; workshops are at <slug>.<host>. */
export const SITE_URL = (process.env.DEMO_SITE_URL ?? 'https://getramplight.com').replace(/\/$/, '');
export const SLUG = process.env.DEMO_SLUG ?? 'summit-autocare';
export const SITE_HOST = new URL(SITE_URL).host;
export const WORKSHOP_URL = `${new URL(SITE_URL).protocol}//${SLUG}.${SITE_HOST}`;
export const VIEWPORT = { width: 1280, height: 800 };

/** Where the finished videos go (inside frontend/). */
export const VIDEO_DIR = process.env.DEMO_VIDEO_DIR ?? 'demo-videos';

/** The password of every demo account. Never committed: set DEMO_PASSWORD when recording. */
export function demoPassword(): string {
  const password = process.env.DEMO_PASSWORD ?? '';
  if (password.length < 10) {
    throw new Error(
      'Set DEMO_PASSWORD (at least 10 characters) before recording, e.g. in PowerShell: ' +
        '$env:DEMO_PASSWORD = "<a strong password>"',
    );
  }
  return password;
}

interface CaptionState {
  chapter: string;
  text: string;
}

// ---- Drawing in the page (runs in the browser) ---------------------------------------------------

function drawCaption(state: CaptionState | null): void {
  let box = document.getElementById('demo-caption');
  if (!state) {
    box?.remove();
    return;
  }
  if (!box) {
    box = document.createElement('div');
    box.id = 'demo-caption';
    box.setAttribute('aria-hidden', 'true');
    Object.assign(box.style, {
      position: 'fixed',
      left: '50%',
      bottom: '22px',
      transform: 'translateX(-50%)',
      width: 'max-content',
      maxWidth: 'min(940px, calc(100vw - 40px))',
      padding: '14px 22px',
      borderRadius: '14px',
      background: 'rgba(15, 23, 42, 0.93)',
      color: '#ffffff',
      font: '600 20px/1.4 system-ui, -apple-system, "Segoe UI", sans-serif',
      boxShadow: '0 10px 30px rgba(0, 0, 0, 0.35)',
      zIndex: '2147483646',
      pointerEvents: 'none',
    });
    document.body.appendChild(box);
  }
  const chapter = document.createElement('div');
  chapter.textContent = state.chapter;
  Object.assign(chapter.style, {
    font: '700 12px/1.2 system-ui, sans-serif',
    letterSpacing: '0.12em',
    textTransform: 'uppercase',
    color: '#5eead4',
    marginBottom: '4px',
  });
  const text = document.createElement('div');
  text.textContent = state.text;
  if (state.chapter) {
    box.replaceChildren(chapter, text);
  } else {
    box.replaceChildren(text);
  }
}

function drawCard(card: { title: string; subtitle: string; step: string } | null): void {
  let el = document.getElementById('demo-card');
  if (!card) {
    el?.remove();
    return;
  }
  if (!el) {
    el = document.createElement('div');
    el.id = 'demo-card';
    el.setAttribute('aria-hidden', 'true');
    Object.assign(el.style, {
      position: 'fixed',
      inset: '0',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '18px',
      padding: '40px',
      textAlign: 'center',
      background: 'linear-gradient(135deg, #0f172a 0%, #134e4a 100%)',
      color: '#ffffff',
      zIndex: '2147483647',
      font: '400 22px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif',
    });
    document.body.appendChild(el);
  }
  const step = document.createElement('div');
  step.textContent = card.step;
  Object.assign(step.style, {
    font: '700 14px/1 system-ui, sans-serif',
    letterSpacing: '0.18em',
    textTransform: 'uppercase',
    color: '#5eead4',
  });
  const title = document.createElement('div');
  title.textContent = card.title;
  Object.assign(title.style, { font: '800 52px/1.15 system-ui, sans-serif', maxWidth: '1000px' });
  const subtitle = document.createElement('div');
  subtitle.textContent = card.subtitle;
  Object.assign(subtitle.style, { maxWidth: '820px', opacity: '0.85' });
  const brand = document.createElement('div');
  brand.textContent = 'Ramplight · getramplight.com';
  Object.assign(brand.style, { position: 'absolute', bottom: '28px', fontSize: '15px', opacity: '0.6' });
  el.replaceChildren(step, title, subtitle, brand);
}

function moveCursor(target: { x: number; y: number; pulse: boolean }): void {
  let cursor = document.getElementById('demo-cursor');
  if (!cursor) {
    cursor = document.createElement('div');
    cursor.id = 'demo-cursor';
    cursor.setAttribute('aria-hidden', 'true');
    Object.assign(cursor.style, {
      position: 'fixed',
      left: `${window.innerWidth / 2}px`,
      top: `${window.innerHeight / 2}px`,
      width: '26px',
      height: '26px',
      marginLeft: '-13px',
      marginTop: '-13px',
      borderRadius: '50%',
      border: '3px solid #ffffff',
      background: 'rgba(20, 184, 166, 0.35)',
      boxShadow: '0 0 0 2px rgba(15, 23, 42, 0.6), 0 4px 14px rgba(0, 0, 0, 0.35)',
      transition: 'left 650ms ease, top 650ms ease, transform 160ms ease',
      zIndex: '2147483647',
      pointerEvents: 'none',
    });
    document.body.appendChild(cursor);
  }
  cursor.style.left = `${target.x}px`;
  cursor.style.top = `${target.y}px`;
  if (target.pulse) {
    cursor.style.transform = 'scale(0.7)';
    window.setTimeout(() => {
      if (cursor) cursor.style.transform = 'scale(1)';
    }, 180);
  }
}

// ---- The presenter ------------------------------------------------------------------------------

export class Presenter {
  private caption: CaptionState | null = null;
  private chapterName = '';
  private chapterCount = 0;
  private readonly startedAt = Date.now();
  /** What the voice says, and when (empty without a narrator). */
  readonly cues: Cue[] = [];

  constructor(
    readonly page: Page,
    private readonly narrator: Narrator | null = createNarrator(),
  ) {
    // A full page load (e.g. sign-up → the new workshop's address) starts a new document:
    // draw the caption again.
    page.on('domcontentloaded', () => {
      void page.evaluate(drawCaption, this.caption).catch(() => undefined);
    });
  }

  /** Minutes:seconds since the recording started, for the chapter list in the console. */
  get elapsed(): string {
    const s = Math.round((Date.now() - this.startedAt) / 1000);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  /** A full-screen chapter card, then the app again. */
  async chapter(title: string, subtitle: string, holdMs = 6500): Promise<void> {
    this.chapterCount += 1;
    this.chapterName = title;
    console.log(`  ${this.elapsed}  Chapter ${this.chapterCount}: ${title}`);
    await this.card(title, subtitle, `Chapter ${this.chapterCount}`, holdMs);
  }

  /** A title card without a chapter number (opening and closing); the voice reads it. */
  async card(title: string, subtitle: string, step: string, holdMs = 8000): Promise<void> {
    const spoken = await this.prepareVoice(`${title}. ${subtitle}`);
    await this.page.evaluate(drawCard, { title, subtitle, step }).catch(() => undefined);
    await this.holdFor(scaled(holdMs), spoken);
    await this.page.evaluate(drawCard, null).catch(() => undefined);
  }

  /** A caption at the bottom; stays until the next one. Holds long enough to read (and hear). */
  async say(text: string, holdMs?: number): Promise<void> {
    const spoken = await this.prepareVoice(text);
    this.caption = { chapter: this.chapterName, text };
    await this.page.evaluate(drawCaption, this.caption).catch(() => undefined);
    const words = text.split(/\s+/).length;
    await this.holdFor(scaled(holdMs ?? Math.max(3500, words * 380)), spoken);
  }

  /** The voice for the next caption or card (fetched before it shows, so they stay in step). */
  private async prepareVoice(text: string): Promise<Clip | null> {
    return this.narrator ? this.narrator.speak(text) : null;
  }

  /** Starts the voice now (on the video's timeline) and waits until both it and `ms` are done. */
  private async holdFor(ms: number, clip: Clip | null): Promise<void> {
    if (clip) {
      this.cues.push({ atMs: Date.now() - this.startedAt, clip });
    }
    await this.page.waitForTimeout(Math.max(ms, clip ? clip.ms + 500 : 0));
  }

  async pause(ms: number): Promise<void> {
    await this.page.waitForTimeout(scaled(ms));
  }

  async goto(url: string): Promise<void> {
    await this.page.goto(url);
    await this.page.evaluate(drawCaption, this.caption).catch(() => undefined);
    await this.pause(900);
  }

  /** Moves the visible cursor to the control. */
  async point(target: Locator): Promise<void> {
    // Hidden controls (a styled file input, say) can't be scrolled to; the cursor then stays put.
    await target.scrollIntoViewIfNeeded({ timeout: 3_000 }).catch(() => undefined);
    const box = await target.boundingBox().catch(() => null);
    if (box) {
      await this.page.evaluate(moveCursor, {
        x: box.x + Math.min(box.width / 2, 120),
        y: box.y + box.height / 2,
        pulse: false,
      });
      await this.pause(750);
    }
  }

  async click(target: Locator): Promise<void> {
    await this.point(target);
    const box = await target.boundingBox().catch(() => null);
    if (box) {
      await this.page
        .evaluate(moveCursor, {
          x: box.x + Math.min(box.width / 2, 120),
          y: box.y + box.height / 2,
          pulse: true,
        })
        .catch(() => undefined);
    }
    await target.click();
    await this.pause(700);
  }

  /** Types like a person would. */
  async type(target: Locator, text: string): Promise<void> {
    await this.point(target);
    await target.click();
    await target.fill('');
    await target.pressSequentially(text, { delay: scaled(55) });
    await this.pause(350);
  }

  /** For date, time and number fields, which take the whole value at once. */
  async fill(target: Locator, value: string): Promise<void> {
    await this.point(target);
    await target.fill(value);
    await this.pause(600);
  }

  async choose(target: Locator, option: string | { label: string }): Promise<void> {
    await this.point(target);
    await target.selectOption(option);
    await this.pause(700);
  }

  async tick(target: Locator, on = true): Promise<void> {
    await this.point(target);
    if (on) {
      await target.check();
    } else {
      await target.uncheck();
    }
    await this.pause(500);
  }

  /** Picks a file; the cursor points at `near` (the visible card) since file inputs are hidden. */
  async upload(target: Locator, file: string, near?: Locator): Promise<void> {
    await this.point(near ?? target);
    await target.setInputFiles(file);
    await this.pause(800);
  }

  /** Clicks a link in the main navigation, or opens its page when the link is not shown. */
  async nav(label: string, path: string): Promise<void> {
    const link = this.page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: label });
    if (await link.isVisible().catch(() => false)) {
      await this.click(link);
    } else {
      await this.goto(new URL(path, this.page.url()).href);
    }
  }

  async scrollBy(pixels: number): Promise<void> {
    await this.page.mouse.wheel(0, pixels);
    await this.pause(900);
  }

  async scrollToTop(): Promise<void> {
    await this.page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
    await this.pause(900);
  }

  async expectToast(text: string | RegExp): Promise<void> {
    await expect(this.page.locator('[data-sonner-toast]').filter({ hasText: text }).first()).toBeVisible();
  }

  // ---- Signing in and out at the workshop address ----------------------------------------------

  /** Signs in with the username, or with the e-mail address (then pass the username too). */
  async signIn(usernameOrEmail: string, password: string, username = usernameOrEmail): Promise<void> {
    if (!/\/login/.test(this.page.url())) {
      await this.goto(`${WORKSHOP_URL}/login`);
    }
    await this.type(this.page.getByLabel('Username or email'), usernameOrEmail);
    await this.fill(this.page.getByLabel('Password'), password);
    await this.click(this.page.getByRole('button', { name: 'Sign in' }));
    await expect(this.page.getByRole('button', { name: `Account menu for ${username}` })).toBeVisible();
    await this.pause(800);
  }

  async signOut(): Promise<void> {
    await this.click(this.page.getByRole('button', { name: /^Account menu for/ }));
    await this.click(this.page.getByRole('menuitem', { name: 'Sign out' }));
    await expect(this.page).toHaveURL(/\/login/);
    await this.pause(600);
  }

  /**
   * Waits for a notification to arrive (events take a few seconds), then opens the bell so the
   * viewer sees it. Returns the notification in the bell's list.
   */
  async openBellWhenNotified(title: string): Promise<Locator> {
    const item = this.page.getByTestId('notification-item').filter({ hasText: title }).first();
    await expect(async () => {
      await this.page.reload();
      await this.page.evaluate(drawCaption, this.caption).catch(() => undefined);
      await this.page.getByTestId('notification-bell').click();
      await expect(item).toBeVisible({ timeout: 4_000 });
    }).toPass({ timeout: 90_000, intervals: [2_000, 4_000, 6_000] });
    await this.point(item);
    return item;
  }
}

// ---- The fixture: one recorded page per test ------------------------------------------------------

export const test = base.extend<{ presenter: Presenter }>({
  // `provide` is Playwright's fixture callback (usually named `use`; renamed so the React
  // hooks lint rule does not mistake it for React's use()).
  presenter: async ({ browser }, provide, testInfo) => {
    const context = await browser.newContext({
      viewport: VIEWPORT,
      locale: 'en-US',
      timezoneId: 'America/New_York',
      bypassCSP: true,
      recordVideo: { dir: testInfo.outputPath('video'), size: VIEWPORT },
    });
    const page = await context.newPage();
    const presenter = new Presenter(page);
    await provide(presenter);
    const video = page.video();
    await context.close();
    const recorded = await video?.path().catch(() => undefined);
    if (recorded) {
      await mkdir(VIDEO_DIR, { recursive: true });
      const name = basename(testInfo.file).replace(/\.spec\.ts$/, '');
      const status = testInfo.status === testInfo.expectedStatus ? '' : '-FAILED';
      const target = join(VIDEO_DIR, `${name}${status}.webm`);
      await copyFile(recorded, target);
      console.log(`  Video: ${target} (${presenter.elapsed})`);
      if (presenter.cues.length > 0) {
        const narration = join(VIDEO_DIR, `${name}${status}.narration.wav`);
        await writeFile(narration, narrationTrack(presenter.cues));
        const mp4 = join(VIDEO_DIR, `${name}${status}.mp4`);
        if (joinWithVideo(target, narration, mp4)) {
          console.log(`  With voice: ${mp4}`);
        } else {
          console.log(
            `  Voice track: ${narration}. Install ffmpeg (winget install Gyan.FFmpeg) to join it ` +
              'to the video automatically.',
          );
        }
      }
    }
  },
});

export { expect };
