# FocusMap Pomodoro — Reference App Comparison

Date: 2026-11-20. Source: the Pomodoro app actually opened by the user on the Nokia C12 Pro. Package resolved from window focus: `com.superelement.pomodoro` (v17.6), **not** `ginlemon.flowerfree` / Focus To-Do — the user launched the other installed Pomodoro app. All observations below are from real device screenshots/UI dumps, not assumed.

## What the reference app does

Dashboard: task-filter list (Today / Tomorrow / This Week / Planned / Calendar / Completed / Tasks), each row showing tracked focus time + task count (`1h 15m  5`). Header has account sign-in, group/sprout/trophy/chart icons. Floating circular 25-minute timer widget. "Create Project" plus tag/folder shortcuts.

Tapping the timer opens a focus panel:
- Task card at top with checkbox, task title ("Resist distractions and stay ..."), 3 pink session markers, dismiss X.
- Large circular dotted progress ring with `25:00`.
- Primary `Start Focus` button.
- Bottom feature row: `Strict Mode`, `Timer Mode`, `Full Screen`, `White Noise`.
- Top: collapse chevron, overflow menu.

Tapping Start Focus does **not** start the timer directly — it routes to a `Background Timer Permissions` onboarding page with a checklist: Ignore Battery Optimization (Set Up), Allow Auto-Startup, Lock app in Recents, Allow Background Activity, Turn off Power Saving Mode.

Honest note: I did not reach the running-timer state, so pause/stop/break behaviour of the reference was not observed.

## Comparison with FocusMap

| Capability | Reference app | FocusMap | Verdict |
|---|---|---|---|
| Node/task-linked sessions | Task card with session markers | Session linked to a mind-map node; node stats roll up | FocusMap deeper (idea→work link) |
| Focus time + count per view | Today/Planned/Tasks show `1h 15m  5` | Today/week/sessions + per-project/per-node analytics | FocusMap richer analytics, fewer time-bucket views |
| Visual timer | Circular dotted ring, seconds shown | Countdown + progress bar, notification chronometer | Reference more visually distinctive |
| Full-screen focus mode | `Full Screen` toggle | Focus Window (4-strip overlay with aperture) | FocusMap's is more functional (touch passthrough) |
| Strict Mode | Explicit toggle | No equivalent | GAP |
| White noise | Explicit toggle | No equivalent | GAP (out of v1 scope: no audio/assets) |
| Background reliability UX | Dedicated permission checklist before starting | Notification permission requested; no battery/autostart checklist | GAP — reference handles the exact reliability concern our QA flagged |
| Calendar view | Dedicated Calendar row | No calendar | GAP (larger feature) |
| Accounts / social | Sign In, group, trophy, charts | Local-first, no account | FocusMap deliberately different (scope restriction) |

## What is worth adopting (small, no new dependencies)

1. **Background reliability onboarding** — a one-time Settings/Focus-screen card that explains battery optimisation, autostart and "lock in Recents", with a shortcut to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. This directly addresses our documented "timer across reboot/battery optimisation NOT TESTED" risk and matches the reference's strongest reliability decision. No new dependency.
2. **Task/session markers on the active node** — we already store `completedSessions`; showing small markers on the active node in the Focus screen would mirror the reference's 3 tomato icons. Pure UI, uses existing data.
3. **Seconds in the countdown** — our `formatClock` already accepts seconds; the Focus screen shows `mm:ss`. Confirmed present from earlier milestone screenshots (24:55) — no change needed.

## What to avoid

- Accounts, social/leaderboard, gamified trophies: explicitly excluded by FocusMap's product scope restrictions.
- White noise: needs audio assets and playback service; out of v1 scope.
- Imitating the reference's permission flow as a blocking gate: FocusMap should explain and offer, not block starting a session.

## Recommendation

Adopt item 1 (background reliability guidance) and item 2 (session markers) only if you want them. Both are small, reviewable, and use existing data. Nothing here requires changing the timestamp-based timer architecture.
