# Privacy Hub

Anti-peek privacy screen. When the phone is tilted sideways, a translucent
dark layer fades in over the whole display. Viewed straight on, the screen
stays bright; viewed from the side, it is dimmed.

Native Android, Kotlin + Jetpack Compose, Material 3.

## Build

```bash
cd C:/Users/ffaay/privacy-hub
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Use

1. Open Privacy Hub.
2. Grant "Display over other apps".
3. Enable "Privacy screen".
4. Tilt the phone left/right; the overlay fades in.

Debug builds include a simulate-tilt slider and the broadcast action
`com.privacyhub.app.DEBUG_SET_TILT` with a double extra `angle`.

## Limit

This is software dimming driven by the phone's rotation sensor. It does not
narrow the physical viewing cone like a privacy film, and it cannot detect a
side viewer while the phone itself stays upright.
