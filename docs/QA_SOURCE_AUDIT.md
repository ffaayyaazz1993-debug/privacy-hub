# FocusMap — Source Code Audit (Phase 8)

Date: 2026-11-20. Scope: full `app/src` (43 Kotlin files, ~3,825 LOC), manifest, Gradle, 4 unit-test classes.
Method: direct source inspection + scan for network/logging/permission usage. Git history was checked but is not treated as proof.

## Verified components

| Area | Evidence |
|---|---|
| Data layer | Room 2.6.1 via KSP; 4 entities (`Project`, `Node`, `FocusSession`, `UserSettings`), DAOs with indices, `exportSchema=true`. Repository centralises writes. |
| Schema | `focusmap.db` version 1; FK `nodes.projectId -> projects.id` with `ON DELETE CASCADE`, indices on `projectId`/`parentId`/`nodeId`/`startTimestamp`. |
| Pomodoro math | `Pomodoro.elapsedSeconds` uses `accumulatedSeconds + (now - resumeTimestamp)/1000`, clamped at 0; countdown is timestamp-based, not an in-memory counter. |
| State machine | `FocusSessionManager` handles start/pause/resume/restart/stop/skip/complete/break selection; completed sessions route through `repo.completeFocusSession` which rolls time into the node. |
| Foreground service | `FocusSessionService` is `exported=false`, `START_STICKY`, schedules completion at the expected end timestamp, rebuilds notification with pause/resume/stop actions. |
| Overlay | `MaskService` + `MaskView`/`HandleView` preserved: 4 `FLAG_NOT_TOUCHABLE` strips around an uncovered aperture, 36dp handles, fraction-based geometry, idempotent `startMask` guard. |
| Export/import | Gson JSON, pure `serialize`/`deserialize`/`remapNodes` helpers, id-remapping merge. |
| Analytics | Daily/weekly/session-count/by-project/by-node/completion/activity flows over Room. |
| Manifest | `MainActivity` exported (launcher); `ReaderActivity` and both services `exported=false`. No exported receivers/providers. |
| No tracking SDKs | Scans found no Firebase/Crashlytics/OkHttp/Retrofit/Timber/`Log.` calls anywhere in Kotlin. |

## Unverified components

- Runtime behaviour of every canvas gesture (pinch, drag, pan, edge touches) under a real finger on 100–500 nodes — only small-map interaction was exercised earlier.
- Overlay touch passthrough on third-party apps after the Phase 8 code changes.
- Long-duration timer accuracy (25 min), device reboot, timezone/clock changes, battery-optimisation modes.
- Import/export through the real Android file picker (SAVE button is disabled at the aggregated Downloads root when driven by adb; logic is unit-tested).
- Large-font, TalkBack, landscape and small-screen layouts.

## Known technical debt

1. **Room has no migration strategy.** Version 1 with no `Migration` objects — any future schema bump without a migration throws `IllegalStateException` and loses access to the DB. Acceptable for v1 first release; must be addressed before schema v2.
2. **`allowBackup=true`** in the manifest — Android may include `focusmap.db` in OS-level backups. Conflict with a strict "local-only" claim (see privacy audit).
3. **`INTERNET` permission is real, not vestigial.** `ReaderActivity` is an in-app WebView browser (`javaScriptEnabled=true`) that loads user-entered `http(s)` URLs. Core FocusMap has no network client, but the shipped app can reach the network.
4. **No instrumentation tests.** `src/test` has 4 JVM test classes; no `src/androidTest` exists — Room migrations, overlay window lifecycle and real gestures have no automated coverage.
5. `FocusSessionEntity` has no FK to `NodeEntity`; orphan prevention is application-level (`deleteNodeSubtree` deletes sessions explicitly).
6. Notification uses platform drawables, not an app icon — Play Store polish item, not a functional defect.

## Potential crash points

- `FocusMapApplication.instance` is `lateinit` and read by services/UI; if a component starts in an unusual process it would throw `UninitializedPropertyAccessException`. Single-process app, so low risk.
- `room.databaseBuilder(...).build()` with no `fallbackToDestructiveMigration()` — a schema change crashes rather than silently losing data (intentional, but must be handled before any version bump).
- `BackupManager.importJson` previously returned success on malformed JSON (fixed this phase).
- `onTimerEnd` reads by `currentId`; if the service is restarted with a null intent, `handle` defaults to `ACTION_SYNC` and re-reads the active session, so this is safe.

## Missing tests (see QA_TEST_REPORT for status)

- Room DAO integration tests (CRUD, cascade delete, migration, corruption).
- Instrumented gesture tests on the canvas.
- Foreground-service lifecycle tests across process death and reboot.
- Overlay window-count/stacking regression test.
- Real file-picker export/import round-trip.

## Security and permission concerns

| Permission | Purpose | Concern |
|---|---|---|
| `INTERNET` | WebView reader (`ReaderActivity`) | Real network capability; document in Data safety. |
| `SYSTEM_ALERT_WINDOW` | Focus Window overlay | Sensitive permission; user-granted only; overlay is optional and disableable. |
| `FOREGROUND_SERVICE` | Focus timer + overlay services | Required; both services internal-only. |
| `POST_NOTIFICATIONS` | Timer/completion notifications | Runtime permission on API 33+. |

No Accessibility, contacts, location or camera permissions. No third-party tracking SDKs.

## Recommended fixes (this phase)

1. Reject malformed/unsupported backups instead of silently reporting success — **fixed** (`BackupManager.importJson` throws; Settings shows failure).
2. Correct pinch-zoom anchor math — **fixed** (pure `ZoomMath.zoomOffset` + regression tests).
3. Add foreign key from `FocusSession` to `Node`/`Project` + Room migration — deferred: requires a schema change, prohibited in this QA phase; documented as debt.
4. Add instrumentation tests for DB/gestures/service — recommended post-beta.
