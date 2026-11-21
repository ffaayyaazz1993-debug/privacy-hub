# Digital Detox — permissions

| Capability | Permission / mechanism | Required? | Notes |
|---|---|---|---|
| Restrict apps | AccessibilityService (`BIND_ACCESSIBILITY_SERVICE`), user enables in Settings | Yes, for enforcement | `TYPE_WINDOW_STATE_CHANGED` only; `canRetrieveWindowContent=false`. No input injection. |
| List launchable apps | `<queries>` MAIN/LAUNCHER | Yes | No `QUERY_ALL_PACKAGES`. |
| Do Not Disturb | `ACCESS_NOTIFICATION_POLICY` | No | User grants in Settings; previous interruption filter saved and restored. |
| Recurring schedules | `AlarmManager.setAndAllowWhileIdle` | No | Inexact; no exact-alarm permission. `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` restore alarms. |
| Overlay | AccessibilityService overlay (`TYPE_ACCESSIBILITY_OVERLAY`) | Yes | Reuses no Focus Window permission; no `SYSTEM_ALERT_WINDOW` addition. |

Force-stopping the app can cause Android to disable the accessibility service;
the Detox screen detects this and reports "advisory only" until re-enabled.
