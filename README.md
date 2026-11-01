# GLFX GAME TURBO

**Maximum real performance. Zero fake optimization.**

A rootless Android gaming toolkit (Kotlin, Jetpack Compose, landscape-only). Package `com.glfx.gameturbo`, minSdk 21, target 34.

## What it does today
- Live CPU, memory, thermal, battery and network monitoring from public Android APIs
- Game library (launcher apps only), per-game modes, launch shortcuts
- One-tap optimization that applies only supported, reversible app-level actions and reports what was done, skipped and impossible
- Frame-timing benchmark and touch lab on GLFX-owned surfaces
- Session history, backup and restore, permission center
- Module catalog with honest status per module (see `docs/FEATURES.md`)

## What it will never do
No root, Shizuku or ADB. No CPU/GPU clock control, no killing background apps, no thermal override, no invented FPS.

## Build
Requires JDK 17 and Android Studio (Koala or newer), or the Android SDK on the command line.

```
gradle wrapper --gradle-version 8.7   # once, creates gradlew and gradle-wrapper.jar
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug
./gradlew :app:assembleRelease        # unsigned; sign with your own key
```
CI: `.github/workflows/android.yml` builds on every push. Never commit keystores (`*.jks`, `keystore.properties` are git-ignored).

## Layout
`core/` design, device, monitoring, network, modules, storage, performance · `feature/` optimization, games, benchmark · `ui/` screens · `MainViewModel` (MVVM).
