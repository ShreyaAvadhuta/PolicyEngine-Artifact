# Experiments

This document describes the three experiments included with this artifact and how each supports a claim made in the paper.

## E1: dynamic app analysis, confirms C1

Reference: Section 3.2 of the paper.

Requirements: an Android emulator or physical device connected through adb.

The APK to test is either the built SampleLocationStalk app (see E2a below for how to build it) or any APK you supply. The real-world stalkerware apps used in the paper come from the Coalition Against Stalkerware Threat List and are not distributed with this artifact (see docs/RELEASE_STATEMENT.md). Create the folder `Analysis-Scripts\sample_apks`, copy the APK into it (the folder can be changed with the -apkFolder parameter), then from the `Analysis-Scripts` folder run:

    .\artifact_automated_script_testing.ps1

The script installs the APK through adb, observes it for 60 seconds, captures dumpsys location and logcat output, then uninstalls the app. A single run against one app takes approximately 90 seconds; the script can be pointed at additional apps, with runtime scaling accordingly.

A line is appended to pe_results.txt reporting the geolocation channels (GPS, Wi-Fi, cellular, geofence) and the underlying API calls the app invoked, such as requestLocationUpdates, getConnectionInfo, and getCellLocation. This confirms C1.

## E2: PolicyEngine, rooted, confirms C2

Reference: Figures 2 and 3, Table 5, Sections 6 to 8 of the paper.

E2a and E2b are alternative paths to the same claim, not two separate results. Completing E2a alone is sufficient; E2b is optional.

### E2a: via the pre-configured AVD (recommended)

Requirements: an x86_64 host with hardware virtualization exposed. See docs/SETUP.md.

Launch the AVD and verify the environment as described in docs/SETUP.md. PolicyEngine is pre-enabled with the System Framework scope in ReLSPosed.

Target app: the included SampleLocationStalk app (Google Maps also works).

1. Build the sample app following SampleLocationStalk/README.md (it needs JDK 21 and Android Studio Panda 2 or newer), then install it with Android Studio's Run button or with `adb install SampleLocationStalk\app\build\outputs\apk\debug\app-debug.apk`.
2. Open the app and grant the location and phone permissions.
3. Add it to PolicyEngine's scope: in the ReLSPosed manager, open Modules, then PolicyEngine, and tick Sample Location Stalk. If the manager has no launcher icon on your AVD (see docs/KNOWN_LIMITATIONS.md), reinstall it from the module folder:

```
adb shell su -c "cp /data/adb/modules/zygisk_lsposed/manager.apk /sdcard/Download/relsposed_manager.apk"
adb pull /sdcard/Download/relsposed_manager.apk
adb install relsposed_manager.apk
```

Then open ReLSPosed from the app drawer. If it does not appear there, find its package with `adb shell pm list packages | findstr lsposed` and launch it with `adb shell monkey -p PACKAGE_NAME -c android.intent.category.LAUNCHER 1`, replacing PACKAGE_NAME with the package name the first command printed.

4. If the sample app's tabs still show real values, reboot the AVD.
5. Check the four tabs against the E2a rows in the README's "Expected outputs and success/failure" table. On the emulator, the Cellular tab lists the seven spoofed towers but does not show a position in Beijing, and the Connected AP line on the Wi-Fi tab may show the emulator's own network (`AndroidWifi`), so judge by the scan list.

The reported location should differ from the device's real location and fall in the trajectory used in the paper's evaluation: central Beijing (Dongcheng district), with the seven built-in waypoints between 39.9125 and 39.9140 N and between 116.4035 and 116.4065 E. Activity can be observed in PowerShell with:

```
adb logcat -t 500 | Select-String "PE2: traj"
```

On Linux or macOS, use `adb logcat -d | grep "PE2: traj"`. The `PE2: traj` lines step through the route, with `idx` changing over time. This confirms C2.

### E2b: via ReLSPosed on a physical device (optional)

Requirements: a physical Android device running Android 14 or later, with Magisk and ReLSPosed installed. The paper's results were obtained on a Google Pixel 8, Android 16 (API 36), Magisk v30.7, ReLSPosed v1.0.2.

Install the ReLSPosed module APK produced by building the foss flavor of PolicyEngine-ReLSPosed-Module/ (see its README for the exact build command and output path), enable it in the ReLSPosed manager, and target the application under test. The expected result is the same as E2a. This step is not required if E2a has already been completed.

## E3: PolicyEngine, rootless, confirms C3

Reference: Section 9.1 and Appendix F of the paper.

Requirements: an Android emulator or physical device. No root is required. The paper (Section 9.1) reports that the LSPatch build evaluated (v0.6) crashes on Android 16 (API 36) due to an ART runtime conflict. This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5, which do not exhibit that crash on Android 16.

There are three separate pieces involved in this experiment: PolicyEngine itself (the module to be embedded), the sample stalkerware app (the target being patched), and LSPatch (the tool that embeds one into the other). None of these are combined in advance; the steps below combine them.

Step 1. Build the PolicyEngine module APK from Rootless-PolicyEngine/app, following Rootless-PolicyEngine/README.md. This produces the module APK that will be embedded into the target app.

Step 2. Install LSPatch (manager.apk, v1.2 (487)) and Shizuku (v13.5) on the target device or emulator, and start Shizuku, following Rootless-PolicyEngine/README.md.

Step 3. In the LSPatch manager app, select the sample stalkerware app as the target to patch, select the PolicyEngine module APK built in Step 1 to embed, and start the patch. Once patching finishes, the manager presents two options: "Export APK" or "Install". Tap "Install" to complete installation directly from the manager with no further action needed.

The application should report a spoofed GPS location consistent with the trajectory described above. Wi-Fi and cellular information should remain unspoofed under this configuration: Wi-Fi requires system-server instrumentation unavailable without root, and cellular support is still in progress. This confirms C3 as scoped in the paper.





























# Experiments

This document describes the three experiments included with this artifact and how each supports a claim made in the paper.

## E1: dynamic app analysis, confirms C1

Reference: Section 3.2 of the paper.

Requirements: an Android emulator or physical device connected through adb.

Place the APK to be tested, either the included sample stalkerware app or a real one, into the folder read by the script (by default .\\sample\_apks, configurable through the \-apkFolder parameter). Run:

.\\artifact\_automated\_script\_testing.ps1

The script installs the APK through adb, observes it for 60 seconds, captures dumpsys location and logcat output, then uninstalls the app. A single run against one app takes approximately 90 seconds; the script can be pointed at additional apps, with runtime scaling accordingly.

A line is appended to pe\_results.txt reporting the geolocation channels (GPS, Wi-Fi, cellular, geofence) and the underlying API calls the app invoked, such as requestLocationUpdates, getConnectionInfo, and getCellLocation. This confirms C1.

## E2: PolicyEngine, rooted, confirms C2

Reference: Figures 2 and 3, Table 5, Sections 6 to 8 of the paper.

E2a and E2b are alternative paths to the same claim, not two separate results. Completing E2a alone is sufficient; E2b is optional.

### E2a: via the pre-configured AVD (recommended)

Requirements: an x86\_64 host with hardware virtualization exposed. See docs/SETUP.md.

Launch the AVD and verify the environment as described in docs/SETUP.md. PolicyEngine is pre-enabled with the System Framework scope in ReLSPosed. To also exercise the per-process Wi-Fi and cellular hooks against a particular app, add that app to the module's scope in the ReLSPosed manager.

Open a location-consuming application, such as Google Maps or the included sample app. The reported location should differ from the device's real location and should fall within the trajectory range used in the paper's evaluation (39.90 to 39.91 degrees north, 116.40 to 116.41 degrees east, Beijing's Dongcheng district). Activity can be observed through:

adb logcat | grep PE2

Expected lines include system\_server loaded, starting trajectory scheduler at startup, followed by repeated trajectory updates and location deliveries. This confirms C2.

### E2b: via ReLSPosed on a physical device (optional)

Requirements: a physical Android device running Android 14 or later, with Magisk and ReLSPosed installed. The paper's results were obtained on a Google Pixel 8, Android 16 (API 36), Magisk v30.7, ReLSPosed v1.0.2.

Install the ReLSPosed module APK produced by building the foss flavor of PolicyEngine-ReLSPosed-Module/ (see its README for the exact build command and output path), enable it in the ReLSPosed manager, and target the application under test. The expected result is the same as E2a. This step is not required if E2a has already been completed.

## E3: PolicyEngine, rootless, confirms C3

Reference: Section 9.1 and Appendix F of the paper.

Requirements: an Android emulator or physical device. No root is required. The paper (Section 9.1) reports that the LSPatch build evaluated (v0.6) crashes on Android 16 (API 36\) due to an ART runtime conflict. This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5, which do not exhibit that crash on Android 16; Android 14 or 15 can be used instead if a strictly paper-matching environment is preferred.

There are three separate pieces involved in this experiment: PolicyEngine itself (the module to be embedded), the sample stalkerware app (the target being patched), and LSPatch (the tool that embeds one into the other). None of these are combined in advance; the steps below combine them.

Step 1\. Build the PolicyEngine module APK from Rootless-PolicyEngine/app, following Rootless-PolicyEngine/README.md. This produces the module APK that will be embedded into the target app.

Step 2\. Install LSPatch (manager.apk, v1.2 or later) and Shizuku (v13.5 or later) on the target device or emulator, and start Shizuku, following Rootless-PolicyEngine/README.md.

Step 3\. In the LSPatch manager app, select the sample stalkerware app as the target to patch, select the PolicyEngine module APK built in Step 1 to embed, and start the patch. Once patching finishes, the manager presents two options: "Export APK" or "Install". Tap "Install" to complete installation directly from the manager with no further action needed.

The application should report a spoofed GPS location consistent with the trajectory described above. Wi-Fi and cellular information should remain unspoofed under this configuration: Wi-Fi requires system-server instrumentation unavailable without root, and cellular support is still in progress. This confirms C3 as scoped in the paper.  
