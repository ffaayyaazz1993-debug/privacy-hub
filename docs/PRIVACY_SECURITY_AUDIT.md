# FocusMap — Privacy & Security Audit (Phase 8)

Date: 2026-11-20. Method: full source scan of 43 Kotlin files + manifest + dependency catalog + runtime observation on a physical Nokia C12 Pro.

## Data handling

All core data (projects, nodes, sessions, settings) lives in a local Room database `focusmap.db` in app-private storage. Export/import uses plain JSON via Android's Storage Access Framework — the user chooses the destination. No account, registration or server is involved in the core workflow.

## Network behaviour

- Core app: **no network client**. Scan for `HttpURLConnection`, OkHttp, Retrofit, Firebase, Crashlytics: zero hits.
- `ReaderActivity`: an **in-app WebView** that loads user-entered `http(s)` URLs; JS enabled, DOM storage disabled, mixed content blocked, no cache. This is the only network path, and it requires the `INTERNET` permission.
- Therefore "works offline" is true for mind maps, Pomodoro, Focus Window and analytics. It is **not** true that the app never touches the network: the optional reader can fetch a URL.

## Permissions

| Permission | Purpose | Assessment |
|---|---|---|
| INTERNET | WebView reader | Legitimate for that feature; declare in Play Data safety. |
| SYSTEM_ALERT_WINDOW | Focus Window 4-strip overlay | Sensitive; user-granted, optional, disableable. No AccessibilityService is used. |
| FOREGROUND_SERVICE | FocusSessionService, MaskService | Required; both `exported=false`. |
| POST_NOTIFICATIONS | Timer + completion notifications | Runtime permission on API 33+. |

No Accessibility, contacts, location, camera, SMS, storage or background-location permissions.

## Component exposure

- `MainActivity`: exported, launcher only (`singleTop`).
- `ReaderActivity`, `MaskService`, `FocusSessionService`: `exported=false`; no exported receivers or providers.
- PendingIntents use `FLAG_IMMUTABLE`.
- No deep links or arbitrary intent handlers.

## Storage & logging

- No `android.util.Log` / Timber calls in Kotlin — no sensitive data in logs from app code.
- `android:allowBackup=true`: OS may include the app database in Google/ADB backups. Users who require strict device-only storage should disable backup at the OS level. Recommended for a future release: `android:allowBackup="false"` or a backup-rules XML excluding `focusmap.db`.
- No credentials, tokens or keys are stored in app data. The release signing keystore lives **outside** the repo and is read from gitignored `local.properties`.

## Export-file handling

- JSON export contains all projects, nodes, sessions and settings in plaintext. Users are responsible for where they save it.
- Import rejects malformed JSON and backup versions newer than supported (Phase 8 fix); previously it silently reported success, which could mislead a user into believing a restore worked.
- Gson deserialisation is bounded to known data classes; no arbitrary class instantiation.

## Findings

| Severity | Finding | Status |
|---|---|---|
| HIGH | Malformed backup reported as a successful 0-project import | **Fixed** (import now throws; UI shows failure) |
| MEDIUM | `allowBackup=true` can copy local data into OS cloud backups | Documented, deferred (manifest behaviour change is out of QA scope) |
| MEDIUM | INTERNET permission + JS-enabled WebView reader contradicts a strict "no network" claim | Documented for Data safety; no SDK/tracking |
| LOW | No certificate pinning / network-security config | Not applicable to core app; optional reader is user-initiated |

## Conclusion

No unresolved CRITICAL security issue. No third-party analytics, ad or tracking SDKs. Core functionality is genuinely offline. The two MEDIUM items (OS backup, optional reader network) must be stated plainly in Play Data safety and release notes.
