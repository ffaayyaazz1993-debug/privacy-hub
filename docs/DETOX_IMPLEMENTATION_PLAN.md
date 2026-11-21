# Digital Detox — implementation plan

## Phase Zero audit (source-verified, current state)
- Package `com.privacyhub.app`; single `:app`; Kotlin 2.1.0; Compose BOM
  2024.12.01; Room 2.6.1 (KSP); Navigation Compose; Gson; no WorkManager,
  no accessibility service today.
- Room is version **3** with non-destructive migrations 1→2→3. Tables:
  projects, nodes, sessions, user_settings, flashcards, review_logs,
  scheduler_settings, references, routines, planned_sessions,
  weekly_reflections.
- Pomodoro truth lives in Room (`FocusSessionEntity`) and
  `FocusSessionManager`: start/pause/resume/restart/stop/skip/complete.
  `FocusSessionService` is the foreground timer and re-reads Room on restart.
  Widgets/tile already delegate to the manager — there is exactly one timer.
- Focus Window = `MaskService` (4-strip uncovered-aperture overlay),
  preserved untouched.
- Repository is the only data facade (`ui/repository()`); all ViewModels take it.
- Navigation tabs: Home, Maps, Focus, Planner, Settings (Review/Analytics live
  under Settings → Tools).
- Permissions today: INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE,
  POST_NOTIFICATIONS, BIND_QUICK_SETTINGS_TILE.

## Chosen restriction mechanism (with honest limits)
Primary: **AccessibilityService-based guard**. It observes only
`TYPE_WINDOW_STATE_CHANGED` for package identity (never content, never
notifications, never clicks) and, while at least one valid activation source is
live, shows a full-screen non-trapping overlay (`TYPE_ACCESSIBILITY_OVERLAY`)
over restricted packages. No root, no device-owner, no input injection.
`android:accessibilityTool="false"` (not an accessibility app). Consent screen
before enabling. Never disguises state: if the service/permission is off, the
UI reports Detox as advisory-only and does **not** claim apps are blocked.

Package visibility: `<queries>` with `MAIN`/`LAUNCHER` intent only — the
narrowest way to list launchable apps; `QUERY_ALL_PACKAGES` is NOT requested.
Apps outside launcher visibility simply cannot be selected (documented).

Fallback/advisory: if accessibility or (optional) notification-policy access
is revoked, activations are recorded but enforcement is explicitly marked
unavailable in the UI and analytics.

## New permissions / policies
- `BIND_ACCESSIBILITY_SERVICE` implied by the service declaration;
  `android.accessibilityservice.AccessibilityService` intent-filter.
- Optional DND: `ACCESS_NOTIFICATION_POLICY` + user grant in Settings;
  never required for Detox to work.
- No exact-alarm permission: schedules use `AlarmManager.setAndAllowWhileIdle`
  (inexact, Doze-friendly); reboot receiver is `BOOT_COMPLETED` +
  `MY_PACKAGE_REPLACED`.
- Play Console: declare "Accessibility service – app blocking / digital
  wellbeing" with store disclosure; `accessibilityTool=false`; no sensitive
  permissions beyond overlay already used by Focus Window.

## Data model (Room v4, migration 3→4, non-destructive)
- `detox_profiles`: id, name, description, isDefault, dndEnabled,
  pauseBehaviour (KEEP/SUSPEND), emergencyEnabled, autoActivate, createdAt.
- `detox_apps`: id, profileId (FK cascade), packageName, label.
- `node_detox`: nodeId (unique index), profileId (nullable), autoActivate.
- `detox_schedules`: id, name, profileId, weekdays (Mon-first 0..6 CSV),
  startMinute, endMinute, enabled, createdAt.
- `detox_activations`: id, sourceType (POMODORO|SCHEDULED|MANUAL), sourceId,
  profileId, startedAt, endsAt (0 = manual/open), overrideUntil,
  status (ACTIVE|RELEASED|ENDED), focusSessionId (nullable).
- Project default profile resolved through `detox_profiles.projectId`
  (nullable global/project scope) rather than altering `projects`.

## Activation coordination
`DetoxCoordinator` (pure Kotlin, unit-tested): active iff ≥1 source has
status ACTIVE and endsAt==0 or > now and overrideUntil <= now. Sources are
independent rows; releasing one never releases others. Precedence for combined
restrictions: union of selected packages; most restrictive DND wins.
Profile resolution order: explicit → node → project → global default.

## Milestones
M1 audit (this doc) · M2 profiles+apps DB/UI · M3 app picker · M4 guard
service/consent · M5 Pomodoro lifecycle hooks · M6 manual Detox sessions ·
M7 schedules · M8 emergency override + optional DND · M9 analytics/node links ·
M10 backup v4, tests, debug/release build, docs.

## Known technical limitations (documented, not hidden)
- True "input locking" is not possible for a normal app; the guard is an
  overlay + forced return experience, not a kernel-level block.
- A determined user can revoke the accessibility service at any time.
- The guard can only restrict launchable apps visible to package queries.
- Website/VPN blocking is out of scope by design (policy review pending).
