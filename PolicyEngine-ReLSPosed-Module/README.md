# PolicyEngine, rooted ReLSPosed module

This directory contains the core PolicyEngine defensive framework, implemented as a ReLSPosed module, as described in Sections 6 through 8 and Appendices A through E of the paper.

## Contents

HookEntry.kt is the module's Xposed entry point, which delegates to LocationHook.initHooks().

LocationHook.kt contains hook registration, trajectory state, the centralized buildSpoofedLocation function, and hooks for Wi-Fi, cellular, geofencing, and raw GNSS.

Xshare.kt is a typed interface over XSharedPreferences, exposing settings such as isStarted, isHookedSystem, isRandomPosition, accuracy, and trajectoryMode.

## Building

Requires the Android SDK and the Kotlin toolchain. This project has two build flavors, foss (default, uses microG/maplibre) and full (uses Google Play Services and Google Maps). The paper's results and this artifact's verified testing both used the foss flavor:

./gradlew assembleFossDebug

This produces:

app/build/outputs/apk/foss/debug/app-foss-arm64-v8a-debug.apk

The build is restricted to the arm64-v8a ABI (no universal APK is produced). This installs correctly on an x86\_64 emulator because current google\_apis system images (API 34 and later) include ARM translation; Android Studio's Run button handles this automatically.

## Installing

On a rooted device or the pre-configured AVD described in ../docs/SETUP.md:

adb install app/build/outputs/apk/foss/debug/app-foss-arm64-v8a-debug.apk

Then enable the module in the ReLSPosed manager, under Modules, and reboot. See ../docs/EXPERIMENTS.md for the full E2 walkthrough and expected output.

## Requirements

Android 14 or later, Magisk, and ReLSPosed. The paper's results were obtained on a Google Pixel 8, Android 16 (API 36), Magisk v30.7, ReLSPosed v1.0.2.  
