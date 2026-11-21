# Recorded product demo

Playwright scripts that record the Ramplight demo videos on the live site, with a visible
cursor, on-screen captions and chapter cards (no voiceover).

| File                     | Video                                                                                                                                                                         | Length (approx.) |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| `01-walkthrough.spec.ts` | Full tour: owner signs up and sets up Summit Auto Care, customer books, advisor checks in and estimates, customer approves, mechanic works, pick-up and history, owner's view | 12–15 min        |
| `02-owner.spec.ts`       | Owner clip: settings, branding, locations, team, overview                                                                                                                     | 2–3 min          |
| `03-customer.spec.ts`    | Customer clip: sign up, add car, book, approve, history                                                                                                                       | 3–4 min          |
| `04-advisor.spec.ts`     | Advisor clip: new booking, board, confirm, check-in, inspection, estimate                                                                                                     | 3–4 min          |
| `05-mechanic.spec.ts`    | Mechanic clip: approved jobs, start, log work, parts, labour, complete                                                                                                        | 2–3 min          |

`01` creates the workshop **summit-auto.getramplight.com** with its owner (`owner`), advisor
(`sara`) and mechanic (`mike`), and the customer `john-carter`. The clips reuse that workshop
and create their own customers behind the scenes, so they can be recorded again any time.

## Record (PowerShell, in `frontend/`)

```powershell
$env:DEMO_PASSWORD = "<a strong password, 10+ characters>"   # every demo account uses it; keep it
pnpm demo:record                                # all five, in order (about 30 minutes)
pnpm demo:record demo/01-walkthrough.spec.ts    # one video
pnpm demo:record --headed                       # watch it while it records
```

The videos are written to `frontend/demo-videos/` as `.webm` (YouTube, Vimeo, Chrome and VLC
play them). A failed run leaves `<name>-FAILED.webm` and a trace in `demo-results/`
(`pnpm exec playwright show-trace demo-results/.../trace.zip`).

Settings: `DEMO_PACE` (1 = normal, 1.3 = slower, 0.5 = quick check), `DEMO_SLUG` (another
workshop address, e.g. to record `01` again: an address can only be signed up once),
`DEMO_SITE_URL` (default `https://getramplight.com`; `http://localhost:3000` for a local run).

**Sharper video:** Playwright's own recording is good enough for sharing; for a crisper file,
run with `--headed` and record the browser window with OBS Studio or the Xbox Game Bar (Win+G).

**Never commit the password.** It protects real accounts on the live site.
