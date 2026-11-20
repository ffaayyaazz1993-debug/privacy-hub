# FocusMap — Technical Audit & Implementation Plan

Target project: `privacy-hub` (Smart Privacy Screen). The existing blackout-overlay
app is the foundation; its privacy window becomes the optional **Focus Window**.
Package stays `com.privacyhub.app` (debug `com.privacyhub.app.debug`) to avoid migration.

## PHASE 1 — AUDIT (existing app)

**Framework / language / toolchain**
- Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Material 3. Single Gradle module `:app`.
- AGP 8.7.3, compileSdk 35, minSdk 26, targetSdk 33, JVM 17. Version catalog present.
- Deps today: core-ktx, activity-compose, lifecycle-runtime-ktx, compose ui/material3/tooling,
  coroutines-android. **No Room, no navigation, no ViewModel-compose, no DataStore, no DI.**
- Architecture today: flat. Compose UI (`ui/PrivacyApp.kt`) talks directly to a SharedPreferences
  store (`privacy/MaskStore`) and starts a bound-less foreground `Service` (`service/MaskService`).
  No domain/data layers, no persistence beyond prefs. ~1093 LOC.

**How the black-screen overlay works (PRESERVE)**
- `MaskService` builds the blackout from **four `FLAG_NOT_TOUCHABLE` strip windows**
  (top/bottom/left/right) placed AROUND a transparent aperture. The aperture has **no overlay
  window over it**, so touches there land directly on the app underneath. This is the workaround
  for Android's obscured-touch/tapjacking rejection that a full-screen overlay triggers on real
  devices. Verified on Nokia C12 Pro (Android 12).
- `MaskView` draws the scrim as ONE even-odd `Path` (outer rect + inner rounded rect). No
  hardware layer / xfermode (avoids GPU-memory compositor freeze on low-end phones).
- Aperture geometry = fractions of the surface (`MaskConfig`), so it survives rotation/screens.
  `MaskStore` persists to SharedPreferences `smart_privacy_prefs`.

**Transparent viewing window / controls (PRESERVE)**
- Movable: MOVE handle (bottom-center). Resizable: RESIZE handle (bottom-right corner).
  Close: CLOSE/X handle (top-right) stops the mask. Handles are 36dp touchable overlay windows.
- Transparency: `dimAlpha` (0.30–0.98) slider in the UI; strip color = argb(dimAlpha*255,0,0,0).
- Smooth drag: on first MOVE past touch-slop, ONE full-screen `MaskView` repaints while strips
  hide; swap synced to the draw pass (pre-draw) + `windowAnimations=0` to kill flicker; drag
  preview forced sharp-corner/no-border to match resting strips; explicit full-screen sizing to
  avoid system-bar inset. Conceal/Reveal/Stop notification actions.
- Touch passthrough: works in the uncovered aperture (no accessibility needed). NOT a privacy
  filter against side-viewing; it is a distraction-reduction scrim.

**Baseline**: `assembleDebug` BUILD SUCCESSFUL (5s). Nokia C12 Pro attached (wireless adb).

**Bugs / limitations identified**
- No structured persistence (projects/nodes/sessions) — everything is prefs.
- No navigation/multi-screen; single settings-style screen.
- Timer, mind map, analytics: absent (to be built).
- Overlay is solid and must be preserved verbatim; it is NOT an app blocker / optical filter.
- targetSdk 33 (fine); foreground-service type not declared (needed if we add a typed FGS later).

## ARCHITECTURE DECISIONS
- Keep single `:app` module but enforce **package-level modularity**: `data/` (Room + repos +
  export), `domain/` (models + pomodoro engine + layout), `ui/` (feature screens + theme +
  components), `service/` (overlay + focus timer). Clean UI→domain→data separation.
- **Preserve unchanged**: `privacy/*`, `service/MaskService.kt`, `ReaderActivity.kt`. Focus Window
  reuses them as-is. No new overlay/accessibility permissions.
- Add: Room 2.6.1 (KSP 2.1.0-1.0.29), navigation-compose 2.8.5, lifecycle-viewmodel/runtime-compose
  2.8.7, gson 2.10.1 (export/import), material-icons-extended. All cached.
- **Settings in Room** (`UserSettings` single row) — matches the spec's entity list and keeps
  export/backup unified. (DataStore not required; avoids an extra uncached dependency.)
- **Timer = foreground service + persisted timestamps**, recalculated on resume/process death.
  No in-memory-only countdown. WorkManager not used (it is for deferrable work, not precise timers).
- Local-first, no network/account/API. Gson JSON export/import for portability.

## MILESTONES
1. **Foundation** — deps, Room schema (Project/MindMapNode/FocusSession/UserSettings), Application,
   Material3 dark/light theme, 5-tab navigation + Home. Overlay preserved & still working.
2. **Mind Map** — project CRUD/search/categories, interactive canvas (pan/zoom/drag, add/rename/
   delete child/sibling, collapse, auto-arrange, fit, undo/redo), node properties/status, persistence.
3. **Pomodoro** — timestamp timer engine, node association, controls, session recording, background
   + process-death restoration, completion notification.
4. **Integration** — start focus from node, auto node/project progress updates, Focus Window wiring.
5. **Analytics** — project/node stats, daily/weekly focus, history, optional learning review.
6. **Production** — tests (mind map/pomodoro/integration/data), performance, export/import/backup,
   compatibility, release APK/AAB.

## SCOPE GUARDRAILS (v1)
No AI, social, leaderboards, collaboration, cloud accounts, subscriptions, ads, or extra permissions.
Focus Window is optional and fully disableable. Keep architecture extensible.