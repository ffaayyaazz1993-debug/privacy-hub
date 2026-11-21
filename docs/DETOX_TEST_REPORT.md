# Digital Detox — test report (2026-11-21)

## Automated
- `./gradlew testDebugUnitTest`: **53 tests, 0 failures, 0 errors** (49 previous +
  4 new `DetoxCoordinatorTest`: priority chain, expiry, override isolation, union).
- `./gradlew assembleDebug assembleRelease bundleRelease`: BUILD SUCCESSFUL (3m57s).
- Room schema v4 exported: `app/schemas/.../4.json`. Migration 3→4 is additive only.

## Device (Nokia C12 Pro, Android 12, wireless adb) — VERIFIED
1. Installed debug build; bottom nav shows 6 tabs incl. Detox; Detox tab opens.
2. Created profile "Study" via UI and added `com.block.juggle` (Block Blast!) via
   the launcher-app picker.
3. Enabled the accessibility guard; UI changed from "advisory only" to "guard: on".
4. Started a manual 30-minute Detox; UI showed active source, 1 restricted app.
5. Launched the restricted app: dark overlay appeared with "You're currently in
   a Focus Session", the app name, profile name, source count, and four buttons
   (screenshot + logcat `overlay added for com.block.juggle`).
6. Tapped "Emergency override 5 min": overlay disappeared; app became usable;
   the Detox source remained active.
7. Tapped "End" in FocusMap: Detox returned to off. Accessibility settings were
   restored to their prior values after testing.

## NOT verified on device
- Automatic Detox activation from a Pomodoro session (code wired in
  `FocusSessionManager`; no device run).
- Scheduled alarm firing, DND apply/restore, reboot restoration.
- Backup v4 import of Detox rows (JVM-tested JSON round-trip only for earlier versions;
  v4 import path compiled but not executed on device).
- Other device vendors / Android versions, Play submission checks.
