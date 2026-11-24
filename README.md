# BlueApp: muscle-memory detox for Android

Your thumb opens TikTok before you've decided to. BlueApp puts a short, **always different** pause in the way, then caps how long a session can last.

## How it works

1. **You open a guarded app** (TikTok, Instagram and YouTube are recommended and pre-selected on first run).
2. **A pause appears: a short mental exercise.** Exercises come in three blocks. Each pause picks one of your enabled blocks at random, then an exercise from it, and never shows the same exercise twice in a row:

   | Block | Exercises |
   |---|---|
   | **Calm** | breathing countdown (random length), box breathing (in, hold, out, hold), notice around you (name things you can see, hear or feel), press and hold, write an intention |
   | **Agility** | mental maths, colour words (tap the ink colour, not the word), numbers in order (scattered dots), count the dots |
   | **Memory** | remember a code, repeat the pattern (squares light up in a 3×3 grid), remember words (spot them in a longer list), spell a word backwards |

   Turn whole blocks on or off from the dashboard, or pick single exercises (and try each one) in **Exercises › Choose exercises**. Keep only the breathing countdown for a timer-only pause.
   Pauses get a little longer the more often you open apps that day. Even the session-length buttons are shuffled, so there's no fixed spot to tap without reading.
3. **You choose how long you need** from your own time options (up to 4, e.g. 2, 5 and 10 min; set on first run and in Settings › Time options). The longest one is your limit per session.
4. **When time's up**, the pause screen comes back: close the app, or do a harder challenge for a short extension (1–5 min).

Leave the app and come back within 60 seconds and your session keeps going. Stay away longer, or lock the phone for longer, and the next open gets a fresh pause. "Not now, take me home" is always one tap away and counts toward "times you walked away" on the dashboard.

## Themes

Dashboard → **Settings › Theme**. Each option previews in its own colors and applies everywhere, including the pause screen.

- **System (Material You):** primary and secondary colors come from your wallpaper and follow dark mode (Android 12+).
- **Blue** (default, graphite with a muted blue), **Black** (true black for OLED, easiest on the battery), **Light**, **Forest**, **Sunset**.

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

## Download

The latest build of `main` is always at **[Releases › Nightly](../../releases/tag/nightly)** (`BlueApp-nightly.apk`). It's rebuilt and replaced on every push to `main`.

## Installing (Play Protect says "App blocked to protect your device")

Google Play Protect blocks *sideloaded* apps that declare an Accessibility service when they are installed from a browser, chat app or file manager. It's a blanket anti-fraud rule, not a finding about this app. BlueApp has no internet permission, so it can't send anything anywhere. Ways in:

1. **USB / ADB (recommended):** `adb install app-release.apk`. ADB installs aren't covered by this block.
2. **Pause Play Protect while installing:** Play Store → your profile icon → Play Protect → ⚙ → turn off *Scan apps with Play Protect*. Install, then turn it back on.
3. **Play Store internal testing:** upload to a Play Console internal track and install from the Play Store. This needs a developer account and an Accessibility declaration.

**Sideloading on Android 13+:** the Accessibility switch is greyed out for apps installed outside the Play Store. Go to *App info → ⋮ → Allow restricted settings*, then turn it on. The onboarding screen has a shortcut for this.

## Build

```bash
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # session-rule tests
```

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36.0.0). Every push also builds the APK on GitHub Actions (download it from the run's *Artifacts*), and pushes to `main` update the Nightly release.

The release build is signed with the debug key so it installs directly. Use your own signing config before distributing it.

## Code map

| File | What it does |
|---|---|
| `GuardService.kt` | Accessibility service: feeds app-switch events to the tracker and opens the pause screen |
| `SessionTracker.kt` | Pure session rules (grace period, expiry, per-app sessions), unit tested |
| `InterceptActivity.kt` | The pause screen: challenge → choose minutes → time's up |
| `Exercises.kt` | The exercise blocks and the picking rules, unit tested |
| `Challenges.kt` | Every exercise screen, randomized each time |
| `ExercisesActivity.kt` | Settings › Exercises: turn each one on or off, try it |
| `OnboardingActivity.kt` | First run: apps → session limit → permissions |
| `MainActivity.kt` | Dashboard: status, today's stats, settings, exercise blocks, preview a pause |
