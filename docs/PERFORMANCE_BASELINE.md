# Performance baseline — 2026-11-21 (pre-optimisation)

Device: Nokia C12 Pro, Android 12, 720x1560, wireless adb. Build: debug APK at
`57fc2ca`. Method: `dumpsys gfxinfo com.privacyhub.app.debug reset`, open each
tab in turn, two 150 ms swipes per tab, then read `Total frames rendered`,
`Janky frames`, p95 and p99. This is a frame-timing proxy, not a full Android
Studio Profiler trace; the device had TapWake accessibility and TalkBack enabled.

| Screen | Janky frames | p95 | p99 |
|---|---:|---:|---:|
| Home | 2.35% | 23 ms | 350 ms |
| Maps (canvas) | 13.21% | 200 ms | 450 ms |
| Focus | 42.53% | 300 ms | 600 ms |
| Planner | 5.76% | 46 ms | 350 ms |
| Detox | 12.63% | 250 ms | 450 ms |
| Settings | 10.78% | 61 ms | 250 ms |

## Identified bottlenecks (evidence-backed)
1. **Focus timer recomposes the whole screen twice per second.**
   `FocusViewModel` has two independent `combine(active, ticker)` StateFlows
   (`remaining`, `progress`) fed by a 500 ms ticker. `FocusScreen` collects
   both at the top level, so every tick recomposes TaskCard, the node session
   markers, the Detox card, the notes field and the timer canvas. Measured worst
   screen: 42.53% janky frames, p95 300 ms.
2. **Detox state is collected outside the ViewModel.** `FocusScreen` collects
   `observeActivations()` and `observeProfiles()` directly, duplicating lifecycle
   collection and adding two more flows to the same recomposition scope.
3. **Session dots use one `Canvas` per dot** in `TaskCard`; cheap individually,
   but they redraw on every timer tick.
4. **Maps canvas is second-worst (13.21% jank, p95 200 ms)** with a 50-node
   stress map loaded; needs a separate pass after the timer fix.
5. Device-wide load is real: `top` showed TapWake ~11 min cumulative CPU and
   `system_server` high; FocusMap itself was idle when not in foreground. This
   does not excuse app-level recomposition waste.

## Plan
Collapse the two tick-driven StateFlows into one 1 s `TimerUiState`, collect it
inside the timer ring only, move Detox status into the ViewModel, then re-measure
the exact same script before touching the canvas.

## After M11.2 fixes (same device, same script, debug build)
- Focus: 20.00% janky, p95 89 ms (was 42.53% / 300 ms).
- Maps: 8.33% janky, p95 77 ms (was 13.21% / 200 ms) — secondary effect of
  fewer Focus-side recompositions; canvas itself not yet refactored.
- Release build (R8): Focus tab transition+swipe p99 150 ms, p95 85 ms.
- Standing timer draw (6 s with a live session): 7 frames, p95 ~30 ms. Still
  measurable jank on this low-end phone; not eliminated. No further guessed
  refactor was made without a trace.

## Changes made
- `FocusViewModel`: two 500 ms tick-driven StateFlows → one 1 s `TimerUiState`;
  Detox status derived in the ViewModel from one combined flow.
- `FocusScreen`: timer clock collected only inside `DottedTimerRing`; Detox card
  reads one string; session dots no longer use one Canvas each.
- `DottedTimerRing`: static tick paths cached with `drawWithCache`; only the
  active prefix and progress arc redraw per tick.
- Debug and release artifacts rebuilt; 53 JVM tests still pass.

## Not yet measured
- Android Studio Profiler / Perfetto system trace (not available in this CLI
  session); 50/100/250/500-node canvas benchmark; battery comparison;
  cold-start timing; DND/guard-on CPU cost.

## iOS-style motion (M11.2 addendum)
- `ui/animation/IosMotion.kt`: 180 ms fade + small vertical slide on screen
  entry (alpha/translation only, GPU layers), spring press-scale on Focus
  primary action and the four mode controls. No layout/Canvas work per frame.
- Maps/canvas destination is deliberately NOT animated: animating it did not
  help and gfxinfo stayed high (~60% jank with a 50-node stress map). This is
  a pre-existing canvas/list cost, not introduced by the motion code.
- Post-motion release gfxinfo (same script): Focus 22.5% jank / p95 46 ms /
  p99 117 ms; Detox 10.8% / 34 ms / 42 ms; Maps 60.6% / 113 ms / 150 ms.
- Debug and release builds green; 53 JVM tests pass.
