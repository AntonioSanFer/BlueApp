# BlueApp: muscle-memory detox for Android

Your thumb opens TikTok before you've decided to. BlueApp puts a short, **always different** pause in the way, then caps how long a session can last.

## How it works

1. **You open a guarded app** (TikTok, Instagram and YouTube are recommended and pre-selected on first run).
2. **A pause appears.** It's picked at random each time, and it's never the same kind twice in a row:
   - breathing countdown (random length)
   - quick maths
   - type a phrase
   - spell a word backwards
   - tap numbered dots in order (they land in new spots every time)
   - press and hold for N seconds
   - count the dots
   - remember a short code
   Pauses get a little longer the more often you open apps that day. Even the session-length buttons are shuffled, so there's no fixed spot to tap without reading.
3. **You choose how long you need**, up to your session limit (5–45 min, set on first run).
4. **When time's up**, the pause screen comes back: close the app, or do a harder challenge for a short extension (1–5 min).

Leave the app and come back within 60 seconds and your session keeps going. Stay away longer, or lock the phone for longer, and the next open gets a fresh pause. "Not now, take me home" is always one tap away and counts toward "times you walked away" on the dashboard.

## Battery

- No foreground service, no polling, no alarms, no wakelocks, **no internet permission**.
- An Accessibility Service subscribed **only** to window-state changes (an app coming to the front), with `canRetrieveWindowContent=false`. Each event costs one hash-set lookup.
- The only timer is a `Handler` callback that exists while a session is running. Because Handler time stops in deep sleep, expiry is also checked against the wall clock on unlock and on every app switch.
- No dependencies beyond the Kotlin stdlib. The release APK is about 80 KB.

## Permissions (asked on first run)

| Permission | Why |
|---|---|
| Accessibility (required) | To know which app just opened. It can't read your screen or your typing. |
| Display over other apps (optional) | Some OEM skins (Xiaomi, Oppo, Vivo, Huawei) block the pause screen without it. |

**Sideloading on Android 13+:** the Accessibility switch is greyed out for apps installed outside the Play Store. Go to *App info → ⋮ → Allow restricted settings*, then turn it on. The onboarding screen has a shortcut for this.

## Build

```bash
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # session-rule tests
```

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36.0.0). Every push also builds the APK on GitHub Actions (download it from the run's *Artifacts*).

The release build is signed with the debug key so it installs directly. Use your own signing config before distributing it.

## Code map

| File | What it does |
|---|---|
| `GuardService.kt` | Accessibility service: feeds app-switch events to the tracker and opens the pause screen |
| `SessionTracker.kt` | Pure session rules (grace period, expiry, per-app sessions), unit tested |
| `InterceptActivity.kt` | The pause screen: challenge → choose minutes → time's up |
| `Challenges.kt` | The eight randomized challenge types |
| `OnboardingActivity.kt` | First run: apps → session limit → permissions |
| `MainActivity.kt` | Dashboard: status, today's stats, apps, limit, preview a pause |
