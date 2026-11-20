# FocusMap — Device Compatibility (Phase 8)

## Physical device used

- Model: Nokia C12 Pro
- Android: 12 (SDK 31)
- Screen: 720x1560, density ~DPI as reported by device
- Connection: wireless adb (`adb` TLS service serial; IP:port drifts between sessions)
- Original overlay verification (earlier milestone): the same device, Android 12

## Tested on this device

| Scenario | Result |
|---|---|
| Install signed debug build over previous build | PASS |
| Cold launch, process survives | PASS |
| Project create + list | PASS |
| Force-stop and relaunch persistence | PASS |
| Analytics dashboard (earlier milestone) | PASS — 7 sections, real data |
| Focus Window overlay (earlier milestone) | PASS — 7 overlay windows on activation, clean teardown |

## Not tested / limitations

- Landscape orientation, display cutouts, gesture vs 3-button navigation, edge-to-edge: NOT TESTED.
- Font scaling, TalkBack, contrast, dark/light toggle on device: NOT TESTED.
- Small-screen (<720px wide) and tablet layouts: NOT TESTED.
- 50–500 node canvas stress and memory profiling: NOT TESTED (Compose canvas controls not addressable via uiautomator).
- Long-duration timer with screen locked, battery optimisation on/off, timezone/clock changes, reboot: NOT TESTED.
- BlueStacks emulator was used in earlier milestones (Android 9); no emulator result is counted as physical-device validation here.
- No iOS / other OEM devices available.

## Device-specific observations

- Wireless adb port drifts; reconnect via `adb mdns services` or the auto-connected TLS serial rather than a cached IP:port.
- Compose `Switch` and canvas controls expose no `android.widget.Switch`/content-desc to `uiautomator dump`, so some UI automation requires screenshot-based interaction not available in this run.
