# Nifty Live Chain — Android App

Same features as the desktop AlgoStraddle web app:
- Live option chain, investments, % Diff, Max/Min with spot
- OHLC, dual alarms, 5-min mute, history, sound

## Build APK (Android Studio)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open this folder: **File → Open** → select `nifty-android`
3. Wait for Gradle sync
4. Connect phone (USB debugging) or use emulator
5. Click **Run** (green play)

### Generate installable APK
**Build → Build Bundle(s) / APK(s) → Build APK(s)**  
APK path: `app/build/outputs/apk/debug/app-debug.apk`  
Copy to phone and install (allow unknown sources).

## Requirements
- Android 7.0+ (API 24)
- Internet permission (already in manifest)
- No login / no API keys

## Note
Sound may need a user tap once (Test Sound) due to Android audio policy.
