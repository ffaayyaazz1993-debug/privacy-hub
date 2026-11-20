# FocusMap — Release Checklist (Phase 8)

## Artifacts (built 2026-11-20, commit pending)

| Artifact | Path | Size |
|---|---|---|
| Signed release APK | `C:\Users\ffaay\privacy-hub\app\build\outputs\apk\release\app-release.apk` | 1,437,959 bytes |
| Signed release AAB | `C:\Users\ffaay\privacy-hub\app\build\outputs\bundle\release\app-release.aab` | 3,451,880 bytes |
| Debug APK | `C:\Users\ffaay\privacy-hub\app\build\outputs\apk\debug\app-debug.apk` | 17,331,803 bytes |
| Lint report | `app/build/reports/lint-results-debug.html` | 0 errors, 53 warnings |
| Test results | `app/build/test-results/testDebugUnitTest/*.xml` | 22 tests, 0 failures |

Copies for distribution: `C:\Users\ffaay\FocusMap-release.apk`, `C:\Users\ffaay\FocusMap-release.aab`, `C:\Usersfaay\FocusMap-debug.apk`.

## Release configuration

- Application ID: `com.privacyhub.app` (debug: `.debug`)
- App name: FocusMap (`res/values/strings.xml`)
- Version: 1.0 (versionCode 1)
- minSdk 26 / targetSdk 33 / compileSdk 35
- Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2024.12.01, Room 2.6.1, KSP 2.1.0-1.0.29
- Signing: release keystore `C:\Users\ffaay\focusmap-release.jks` (outside repo), credentials in gitignored `local.properties`; R8 minify + resource shrink enabled; apksigner confirmed the FocusMap cert
- Permissions: INTERNET (WebView reader), SYSTEM_ALERT_WINDOW (optional Focus Window), FOREGROUND_SERVICE, POST_NOTIFICATIONS

## Beta gate

- [x] Clean debug build succeeds
- [x] Release APK and AAB build successfully and are signed
- [x] Critical unit tests pass (22/22)
- [x] Mind-map data persists after force-stop (device)
- [x] Node-linked Pomodoro accuracy verified in earlier milestones (device) and by unit tests (timestamp math)
- [~] Background timer behaviour: verified earlier for background/process death; reboot/clock-change NOT TESTED
- [~] Focus Window works without breaking touch (earlier milestone device verification; not re-run after Phase 8 fixes)
- [x] Analytics reconciles with session records (unit tests + earlier device render)
- [~] JSON export/import preserves data integrity: logic unit-tested; real picker not automatable here
- [x] No unresolved CRITICAL security issue (see privacy audit)
- [x] Device-testing limitations documented

## Play Store submission — outstanding

- [ ] Data safety declaration reflecting INTERNET (WebView reader) and local storage / OS backup behaviour
- [ ] Replace platform notification icons with app icons; adaptive icon verification
- [ ] Privacy policy URL (required if listing declares network access)
- [ ] Instrumentation tests for DB/gestures/service
- [ ] Wider device matrix (landscape, cutouts, font scaling, TalkBack)
- [ ] Version bump before any store upload (currently 1.0 / code 1)

Do not upload automatically. Signing keys and passwords must never be committed.
