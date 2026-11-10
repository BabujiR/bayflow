import { copyFileSync, existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { stripVTControlCharacters } from 'node:util';

import type { Reporter, TestCase, TestResult } from '@playwright/test/reporter';

/**
 * Collects the scenario videos into one folder (default `e2e-videos/`) with an index.html
 * player: pick a scenario, and every actor's video plays side by side, in sync, with the
 * captions listed as a clickable timeline.
 */

interface Timeline {
  videos: { actor: string; offsetMs: number; path: string }[];
  steps: { t: number; actor: string; text: string; kind: string }[];
  durationMs: number;
}

interface Entry {
  id: string;
  title: string;
  spec: string;
  project: string;
  status: string;
  error?: string;
  durationMs: number;
  videos: { actor: string; offsetMs: number; file: string }[];
  steps: Timeline['steps'];
}

const slug = (s: string) =>
  s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 70);

export default class VideoReporter implements Reporter {
  private readonly outDir: string;
  private readonly entries: Entry[] = [];

  constructor(options: { outputDir?: string } = {}) {
    this.outDir = resolve(options.outputDir ?? 'e2e-videos');
  }

  onBegin(): void {
    rmSync(this.outDir, { recursive: true, force: true });
    mkdirSync(this.outDir, { recursive: true });
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    const attachment = result.attachments.find((a) => a.name === 'bayflow-timeline' && a.body);
    if (!attachment?.body) {
      return;
    }
    const timeline = JSON.parse(attachment.body.toString('utf8')) as Timeline;
    const project = test.parent.project()?.name ?? 'default';
    const spec = test.location.file.split(/[\\/]/).pop() ?? '';
    const id = `${String(this.entries.length + 1).padStart(2, '0')}-${project}-${slug(test.title)}`;
    const dir = join(this.outDir, id);
    mkdirSync(dir, { recursive: true });
    const videos = timeline.videos
      .filter((v) => existsSync(v.path))
      .map((v, i) => {
        const file = `${i + 1}-${slug(v.actor)}.webm`;
        copyFileSync(v.path, join(dir, file));
        return { actor: v.actor, offsetMs: v.offsetMs, file: `${id}/${file}` };
      });
    this.entries.push({
      id,
      title: test.title,
      spec,
      project,
      status: result.status,
      error: result.error?.message ? stripVTControlCharacters(result.error.message).slice(0, 1500) : undefined,
      durationMs: timeline.durationMs,
      videos,
      steps: timeline.steps,
    });
  }

  onEnd(): void {
    writeFileSync(join(this.outDir, 'scenarios.json'), JSON.stringify(this.entries, null, 2));
    writeFileSync(join(this.outDir, 'index.html'), PLAYER.replace('__DATA__', JSON.stringify(this.entries).replace(/</g, '\\u003c')));
    console.log(`\nScenario videos: ${join(this.outDir, 'index.html')}`);
  }
}

const PLAYER = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bayflow e2e scenarios</title>
<style>
  :root { --bg:#f6f7f9; --card:#fff; --ink:#0f172a; --muted:#475569; --line:#e2e8f0; --blue:#1d4ed8; }
  * { box-sizing: border-box; }
  body { margin:0; font:15px/1.45 system-ui, sans-serif; color:var(--ink); background:var(--bg); }
  header { padding:16px 20px; background:#0f172a; color:#fff; display:flex; gap:16px; align-items:baseline; flex-wrap:wrap; }
  header h1 { margin:0; font-size:20px; }
  header span { color:#cbd5e1; font-size:14px; }
  .layout { display:flex; flex-wrap:wrap; }
  nav { flex:1 1 300px; max-width:100%; padding:12px; border-right:1px solid var(--line); background:var(--card); }
  nav button { display:flex; gap:8px; width:100%; text-align:left; border:0; background:transparent; padding:10px; border-radius:10px; font:inherit; color:inherit; cursor:pointer; align-items:flex-start; }
  nav button[aria-current="true"] { background:#eff6ff; outline:2px solid var(--blue); }
  nav small { display:block; color:var(--muted); }
  .pill { flex:none; font-size:12px; font-weight:700; padding:2px 8px; border-radius:999px; }
  .passed { background:#dcfce7; color:#166534; } .failed, .timedOut, .interrupted { background:#fee2e2; color:#991b1b; } .skipped { background:#f1f5f9; color:#475569; }
  main { flex:999 1 560px; min-width:0; padding:16px 20px; display:grid; gap:14px; align-content:start; }
  .controls { display:flex; gap:12px; align-items:center; flex-wrap:wrap; background:var(--card); border:1px solid var(--line); border-radius:14px; padding:10px 14px; }
  .controls button { height:40px; padding:0 16px; border-radius:10px; border:0; background:var(--blue); color:#fff; font:600 15px system-ui; cursor:pointer; }
  .controls input[type=range] { flex:1; min-width:160px; }
  .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(360px, 1fr)); gap:12px; }
  figure { margin:0; background:#000; border-radius:12px; overflow:hidden; position:relative; }
  figure video { width:100%; display:block; }
  figcaption { position:absolute; top:8px; left:8px; background:rgba(15,23,42,.85); color:#fff; font-size:13px; font-weight:700; padding:3px 8px; border-radius:8px; }
  figure.waiting video { opacity:.25; }
  .error { background:#fef2f2; border:1px solid #fecaca; color:#991b1b; border-radius:12px; padding:12px; white-space:pre-wrap; font:13px ui-monospace, monospace; }
  ol { margin:0; padding:0; list-style:none; background:var(--card); border:1px solid var(--line); border-radius:14px; max-height:340px; overflow:auto; }
  ol li button { width:100%; display:flex; gap:10px; text-align:left; border:0; border-bottom:1px solid var(--line); background:transparent; padding:8px 12px; font:inherit; color:inherit; cursor:pointer; }
  ol li button.now { background:#eff6ff; }
  .t { flex:none; width:52px; color:var(--muted); font-variant-numeric:tabular-nums; }
  .who { flex:none; width:170px; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .edge .what { color:#92400e; font-weight:600; } .check .what { color:#166534; }
</style>
</head>
<body>
<header><h1>Bayflow end-to-end scenarios</h1><span id="summary"></span></header>
<div class="layout">
  <nav id="list" aria-label="Scenarios"></nav>
  <main>
    <h2 id="title" style="margin:0;font-size:20px"></h2>
    <div id="error"></div>
    <div class="controls">
      <button id="play" type="button">Play</button>
      <input id="scrub" type="range" min="0" max="1000" value="0" aria-label="Position">
      <span id="clock" style="font-variant-numeric:tabular-nums">0:00</span>
      <label><input id="speed" type="checkbox"> 2× speed</label>
    </div>
    <div id="videos" class="grid"></div>
    <ol id="steps" aria-label="Timeline"></ol>
  </main>
</div>
<script>
const DATA = __DATA__;
let current = null, playing = false, t = 0, last = 0;
const $ = (id) => document.getElementById(id);
const fmt = (ms) => { const s = Math.max(0, Math.floor(ms / 1000)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
const passed = DATA.filter(d => d.status === 'passed').length;
$('summary').textContent = DATA.length + ' scenarios · ' + passed + ' passed · ' + (DATA.length - passed) + ' not passed';

DATA.forEach((d, i) => {
  const b = document.createElement('button');
  b.type = 'button';
  b.innerHTML = '<span class="pill ' + d.status + '">' + d.status + '</span><span><strong></strong><small></small></span>';
  b.querySelector('strong').textContent = d.title;
  b.querySelector('small').textContent = d.spec + ' · ' + d.project + ' · ' + fmt(d.durationMs);
  b.onclick = () => select(i);
  $('list').appendChild(b);
});

function select(i) {
  current = DATA[i]; t = 0; playing = false; $('play').textContent = 'Play';
  [...$('list').children].forEach((b, j) => b.setAttribute('aria-current', String(i === j)));
  $('title').textContent = current.title;
  $('error').innerHTML = '';
  if (current.error) { const e = document.createElement('div'); e.className = 'error'; e.textContent = current.error; $('error').appendChild(e); }
  $('videos').innerHTML = '';
  current.videos.forEach(v => {
    const f = document.createElement('figure');
    const video = document.createElement('video');
    video.src = v.file; video.muted = true; video.preload = 'auto'; video.playsInline = true;
    video.dataset.offset = v.offsetMs;
    const c = document.createElement('figcaption'); c.textContent = v.actor;
    f.append(video, c); $('videos').appendChild(f);
  });
  $('steps').innerHTML = '';
  current.steps.forEach(s => {
    const li = document.createElement('li'); const b = document.createElement('button'); b.type = 'button';
    b.className = s.kind; b.dataset.t = s.t;
    b.innerHTML = '<span class="t"></span><span class="who"></span><span class="what"></span>';
    b.querySelector('.t').textContent = fmt(s.t);
    b.querySelector('.who').textContent = s.actor;
    b.querySelector('.what').textContent = (s.kind === 'edge' ? 'Edge case: ' : s.kind === 'check' ? '✓ ' : '') + s.text;
    b.onclick = () => { t = s.t; sync(true); };
    li.appendChild(b); $('steps').appendChild(li);
  });
  sync(true);
}

function sync(force) {
  if (!current) return;
  $('scrub').value = String(Math.round((t / current.durationMs) * 1000));
  $('clock').textContent = fmt(t) + ' / ' + fmt(current.durationMs);
  const rate = $('speed').checked ? 2 : 1;
  document.querySelectorAll('#videos video').forEach(v => {
    const local = (t - Number(v.dataset.offset)) / 1000;
    v.parentElement.classList.toggle('waiting', local < 0);
    const target = Math.max(0, local);
    if (force || Math.abs(v.currentTime - target) > 0.4) { try { v.currentTime = target; } catch (e) {} }
    v.playbackRate = rate;
    if (playing && local >= 0 && !(v.duration && target >= v.duration)) { if (v.paused) v.play().catch(() => {}); } else if (!v.paused) v.pause();
  });
  let now = -1;
  current.steps.forEach((s, i) => { if (s.t <= t) now = i; });
  [...$('steps').querySelectorAll('button')].forEach((b, i) => b.classList.toggle('now', i === now));
}

function tick(ts) {
  if (playing && current) {
    t += (ts - last) * ($('speed').checked ? 2 : 1);
    if (t >= current.durationMs) { t = current.durationMs; playing = false; $('play').textContent = 'Play'; }
    sync(false);
  }
  last = ts; requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
$('play').onclick = () => { if (!current) return; if (t >= current.durationMs) t = 0; playing = !playing; $('play').textContent = playing ? 'Pause' : 'Play'; sync(true); };
$('scrub').oninput = (e) => { if (!current) return; t = (Number(e.target.value) / 1000) * current.durationMs; sync(true); };
if (DATA.length) select(0);
</script>
</body>
</html>`;
