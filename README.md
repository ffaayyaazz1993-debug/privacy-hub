# Smart Privacy Screen

A software privacy utility for Android. It covers the display with a blackout
mask and leaves a small movable viewing window, so less information is exposed
to people beside you.

**Honest boundary:** this reduces visible information; it does NOT physically
block light from reaching a side viewer. It is not optical privacy glass.

## Features (v1)

- Blackout overlay over other apps with a transparent rounded viewing window.
- Adjustable darkness, window size/position, and corner radius.
- Draggable move handle + resizable corner handle (small touchable overlays;
  the main mask is non-touchable so the app underneath still works).
- Presets: Center, Top strip, Small.
- Quick Conceal / Reveal (also in the foreground-service notification).
- Private reader: a WebView behind the same mask; history/cookies wiped on close.

## Build

```bash
cd C:/Users/ffaay/privacy-hub
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Use

1. Open Smart Privacy Screen.
2. Grant "Display over other apps".
3. Toggle "Privacy mask" on.
4. Drag the round handle to move the window; drag the square corner to resize.
5. Use Conceal for an instant near-full blackout; Reveal to restore.

## Permissions

- SYSTEM_ALERT_WINDOW (draw the mask over other apps)
- INTERNET (private reader)
- POST_NOTIFICATIONS + FOREGROUND_SERVICE (keep the mask running)

## Not in v1

Notification-preview shield, camera-based peek detection, tilt-to-black.
