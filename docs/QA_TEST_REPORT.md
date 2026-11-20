# FocusMap — QA Test Report (Phase 8)

Date: 2026-11-20. Device: Nokia C12 Pro, Android 12 (SDK 31), 720x1560, connected over wireless adb. All device tests used synthetic data only (`QA_50_Node_Stress`); no user data was deleted.

## Build & static analysis

| Test | Command | Result |
|---|---|---|
| Clean + full matrix | `./gradlew --no-daemon clean testDebugUnitTest assembleDebug assembleRelease bundleRelease lint` | PASS (2m26s, exit 0) after a transient daemon file-lock retry |
| Post-fix regression | `./gradlew --no-daemon testDebugUnitTest assembleDebug` | PASS (47s, exit 0) |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 17,331,803 bytes |
| Release APK (signed, R8) | `app/build/outputs/apk/release/app-release.apk` | 1,437,959 bytes |
| Release AAB (signed) | `app/build/outputs/bundle/release/app-release.aab` | 3,451,880 bytes |
| Lint | `lint-results-debug.xml` | 0 errors, 53 warnings (deprecations, OldTargetApi, AGP/dependency update notices) |
| Compilation | Kotlin `e:` errors | 0 |

## Unit tests (22 total, all pass)

- `PomodoroTest`: 6/6 — timer math incl. banked time, paused clock, process-death elapsed, clamping, progress.
- `MindMapLayoutTest`: 5/5 — root, sided children, collapsed branch, nested chain, bounds.
- `BackupManagerTest`: 4/4 — round-trip, malformed JSON, remap hierarchy, multiple roots.
- `AnalyticsTest`: 3/3 — time-by-node, completion %, empty project.
- `ZoomMathTest`: 3/3 — pure zoom, zoom+pan anchor, factor-one pan. (One initial assertion was wrong — corrected before the final green run; no production defect.)

## Device tests (physical Nokia C12 Pro)

| Test | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| Launch | Install debug APK, launch | Process alive | PID present | PASS |
| Project CRUD | Create `QA_50_Node_Stress` | Project listed | Visible in Maps | PASS |
| Persistence | Force-stop, relaunch, reopen Maps | Project restored | `QA_50` visible | PASS |
| Analytics | Dashboard after seeded session data | 7 sections render | Earlier milestone verification: 7 sections rendered | PASS (earlier milestone) |
| Focus Window | Toggle ON during session -> 7 overlay windows | 7 overlays | Verified in M4 (7 overlays). This phase's adb harness could not locate the Compose Switch (no `android.widget.Switch` node), so not re-verified here | NOT RE-TESTED (automation limit) |
| 50/100/250/500-node stress | Add nodes on canvas | Measure render time | Compose canvas FABs expose no text/content-desc to uiautomator; automated insertion not achievable | NOT TESTED |
| 25-min timer background/reboot/clock-change | Long-duration scenarios | Accurate elapsed | Not exercised this phase (time-boxed) | NOT TESTED |
| Touch passthrough via aperture | Tap/scroll third-party app | Native touches land | Verified in earlier milestone on this device; not repeated post-fix | NOT RE-TESTED |

Evidence: `qa_device_results.json` in the Hermes scratch dir; `qa_build_all2.log`, `qa_build_after_fixes2.log` in the repo root.

## Bugs discovered & fixed

| Severity | Bug | Fix |
|---|---|---|
| HIGH | `BackupManager.importJson` returned 0 on malformed JSON; Settings showed "Imported 0 project(s)" — a false success that could make a user believe a restore worked | Throw on malformed/unsupported/newer-version backups; UI shows failure |
| MEDIUM | Pinch-zoom offset formula double-counted centroid pan, causing a pan jump when zooming | Replaced with pure `ZoomMath.zoomOffset`, anchored to previous centroid; 3 regression tests |

## Remaining limitations

- No instrumentation tests; Room migration, overlay lifecycle and real gestures lack automated coverage.
- Foreground timer across device reboot is not guaranteed: no `BOOT_COMPLETED` receiver; the app resyncs the open session on next launch. Timestamp math stays correct, but no completion notification fires while the app has not been opened.
- Wall-clock changes (manual clock/timezone) can distort elapsed time; documented, not fixed (would require schema change to `elapsedRealtime`).
- Import/export via the real file picker is not automatable here (DocumentsUI SAVE disabled at aggregated Downloads root when driven by adb); logic is unit-tested.
- `allowBackup=true` and the optional WebView reader's INTERNET permission are documented in the privacy audit.

## Release readiness

Engineering-complete: YES (clean debug + signed release APK/AAB + 22 unit tests green).
Emulator-tested: earlier milestones ran BlueStacks; this phase used no emulator.
Physical-device-tested: PARTIAL (launch, CRUD, persistence, earlier analytics/Focus Window verification on Nokia C12 Pro).
Beta-ready: YES with documented limitations.
Google Play submission-ready: NO — Data safety declarations, app icon polish, instrumentation tests and Play Store metadata still outstanding.
