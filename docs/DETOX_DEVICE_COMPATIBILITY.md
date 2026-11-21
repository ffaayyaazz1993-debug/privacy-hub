# Digital Detox — device compatibility

- Verified: Nokia C12 Pro, Android 12 (SDK 31), 720x1560 — see `DETOX_TEST_REPORT.md`.
- `TYPE_ACCESSIBILITY_OVERLAY` requires API 22+; app min SDK is higher, so OK.
- Force-stop or app update may disable the accessibility service on some OEMs;
  the UI detects and reports this instead of silently claiming enforcement.
- OEM battery optimizers can delay inexact `setAndAllowWhileIdle` alarms; schedules
  are best-effort. No exact-alarm permission is requested.
- Apps that are not launchable/visible through MAIN/LAUNCHER queries cannot be selected.
- UK/Play policy: app must disclose the accessibility service as digital-wellbeing
  restriction, never as an accessibility tool for users with disabilities.
