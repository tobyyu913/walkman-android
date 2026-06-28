# Walkman (Android)

An Android app styled after the **Sony TPS-L2 Walkman** — silver top, cobalt-blue body,
vertical SONY / WALKMAN lettering, and a cassette window with two big spinning reels.
It **mirrors and controls whatever is currently playing** on the device (Spotify, YouTube
Music, etc.) through Android's system media session.

## Install

Grab the APK from the [latest release](../../releases/latest), or build it yourself:

```bash
JAVA_HOME=<a JDK 17> ./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

On first launch, grant **Notification access** when prompted (Settings ▸ Notification
access). That's what lets the app read and control the active media session — it does not
read your notifications.

## How it works

- `PlayerViewModel` reads the active `MediaController` (preferring Spotify) via
  `MediaSessionManager`, and sends transport commands back to it.
- `MediaListener` is an empty `NotificationListenerService`; enabling it is what unlocks
  access to media sessions.
- `WalkmanScreen` is the Jetpack Compose UI — the cassette body, reels (Canvas), and
  transport keys.

## Requirements

- Android 8.0+ (minSdk 26), portrait.
- Built with AGP 8.7 / Kotlin 2.0 / Compose; needs JDK 17 to build.
