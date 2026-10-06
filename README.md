# PolicyEngine: A Defensive Framework Against Cross-Channel Location Validation in Android Stalkerware

This repository contains the artifact for the ACSAC 2026 submission. PolicyEngine is a Zygisk-based Android defensive framework that supplies geographically consistent synthetic location data to stalkerware and other location-monitoring applications across all four primary Android geolocation channels: GPS, Wi-Fi, cellular, and raw GNSS.

Badges requested: Available, Functional, Reproduced.

Permanent archive (Available badge): this repository is archived on Zenodo. The DOI is listed in the HotCRP submission and will be added to this README in the final packaged version.

## Start here:

### Supported platforms and versions

| Item | Version |
| :---- | :---- |
| Host OS | Windows with PowerShell, where every step below was run. Linux and macOS hosts with hardware virtualization should also work for the AVD steps but were not tested. |
| Android SDK | Emulator and platform-tools (adb) installed, with `emulator` and `adb` on the PATH. To build the sample app: Android SDK Platform 36 and Build-Tools 36.1.0. |
| AVD | `Rooted_Pixel_8_API_36`: Android 16 (API 36), google_apis, x86_64 |
| On the AVD | Magisk 30.6, ReLSPosed 1.0.2 (7211), PolicyEngine module (foss flavor, version 0.0.6) |
| Sample app build | JDK 21 and Android Gradle Plugin 9.0.1. In Android Studio, use Panda 2 (2025.3.2) or newer. The command-line build below needs no IDE. |
| Rootless (E3) | LSPatch v1.2 (487) from https://github.com/JingMatrix/LSPatch, Shizuku v13.5 |
| Paper results | Google Pixel 8, Android 16 (API 36), Magisk v30.7, ReLSPosed v1.0.2 |

### Requirements

| Requirement | Detail |
| :---- | :---- |
| Hardware | x86_64 host with hardware virtualization (Intel VT-x or AMD-V, or KVM on Linux). No special hardware. |
| Memory | Enough host RAM to run the Android emulator. The AVD's own RAM setting is `hw.ramSize` in `Rooted-Emulator-AVD/Rooted_Pixel_8_API_36.avd/config.ini`. |
| Disk | The disk image is about 2.7 GB. Allow at least 6 GB free for the AVD folder after first boot. |
| GPU | Not required. |
| GUI | Required. The emulator window and the sample app's tabs are used. |
| Network | Needed to download the disk image and build dependencies. At run time, only the sample app's Wi-Fi and Cellular position lookups use the network. |
| API keys | None for PolicyEngine. Wi-Fi and cell data are hardcoded, and this artifact ships without a Google Maps key, so trajectories use a built-in route. The sample app's position lookup uses an optional Google Geolocation API key, which can be requested from the authors through HotCRP. |
| Licensing | No paid or commercial software is needed. See the License section below. |

### Minimal check (about 10 minutes, no build)

1. Download the disk image from the link in `docs/SETUP.md` and place it at `Rooted-Emulator-AVD/Rooted_Pixel_8_API_36.avd/userdata-qemu.img.qcow2`.
2. Install the AVD. In PowerShell, from the repository root:
```
Copy-Item -Recurse Rooted-Emulator-AVD\Rooted_Pixel_8_API_36.avd $env:USERPROFILE\.android\avd\
Copy-Item Rooted-Emulator-AVD\Rooted_Pixel_8_API_36.ini $env:USERPROFILE\.android\avd\
```
On Linux or macOS, use `cp -r` into `~/.android/avd/`. Then open the copied `Rooted_Pixel_8_API_36.ini` and set its `path=` line to the absolute location of the copied `.avd` folder on your machine.
3. Launch the AVD:
```
emulator -avd Rooted_Pixel_8_API_36 -no-snapshot-load
```
4. When it has booted, verify the environment from a second terminal:
```
adb shell
su
magisk -v
ls /data/adb/modules
cat /data/adb/modules/zygisk_lsposed/module.prop
exit
exit
```
The Magisk and ReLSPosed app icons may be missing on this AVD. That is expected (see `docs/KNOWN_LIMITATIONS.md`), so use these commands instead.
5. Open Google Maps in the emulator. It is already in PolicyEngine's scope on this image. Then watch the log:
```
adb logcat | findstr PE2
```
(use `grep PE2` on Linux or macOS).

Success: `magisk -v` prints `30.6:MAGISK:R`, `zygisk_lsposed` is listed with ReLSPosed v1.0.2 (7211), `PE2: traj` lines appear repeatedly, and Maps places the device in Beijing's Dongcheng district.
Failure: no `PE2` lines, or Maps shows another location. Reboot the AVD and check that `zygisk_lsposed` is listed.

### Full evaluation (about 30 minutes, excluding the 2.7 GB download and first-time SDK and Gradle downloads)

**Sample app build (used by E1 and E2a).** In PowerShell, from the repository root:
```
Set-Content SampleLocationStalk\local.properties ("sdk.dir=" + $env:LOCALAPPDATA.Replace('\','\\') + "\\Android\\Sdk")
cd SampleLocationStalk
.\gradlew.bat assembleDebug
cd ..
```
If your SDK is elsewhere, write its path into `local.properties`. On Linux or macOS, use `echo "sdk.dir=$HOME/Android/Sdk" > SampleLocationStalk/local.properties` and `./gradlew assembleDebug`. The APK is at `SampleLocationStalk/app/build/outputs/apk/debug/app-debug.apk`.

Optional: before building, set the `GOOGLE_API_KEY` constant in `WifiFragment.java` and `CellularFragment.java`. Without a key, the Wi-Fi and Cellular tabs still show the spoofed scan data but do not resolve a position. Do not commit a key.

**E1: dynamic analysis (about 5 human-minutes and 2 compute-minutes for one app, about 90 seconds for each additional app).** With the AVD running:
```
mkdir Analysis-Scripts\sample_apks
copy SampleLocationStalk\app\build\outputs\apk\debug\app-debug.apk Analysis-Scripts\sample_apks\
cd Analysis-Scripts
.\artifact_automated_script_testing.ps1
```
Success: `pe_results.txt` gains a line for the app that lists the geolocation channels and API calls it used. For the sample app these include GPS, Wi-Fi, and cellular channels and calls such as `requestLocationUpdates`, `getConnectionInfo`, `getScanResults`, and `getAllCellInfo`.
Failure: the file stays empty or the install step fails (check `adb devices`).

**E2a: PolicyEngine on the rooted AVD (about 10 minutes).** Install the sample app, grant the location and phone permissions when prompted, and add it to PolicyEngine's scope:
```
adb install SampleLocationStalk\app\build\outputs\apk\debug\app-debug.apk
adb shell
su
sqlite3 /data/adb/lspd/config/modules_config.db "INSERT INTO scope (mid, app_pkg_name, user_id) SELECT mid, 'com.example.samplelocationstalk', 0 FROM modules WHERE module_pkg_name = 'io.github.jqssun.policyengine2';"
exit
exit
adb reboot
```
If the ReLSPosed manager is visible on your AVD, you can add `com.example.samplelocationstalk` to PolicyEngine's scope there instead. After the reboot, open the sample app and check its four tabs.

| Tab | Success |
| :---- | :---- |
| GPS | Coordinates between 39.9125 and 39.9140 N and 116.4035 and 116.4065 E (the seven built-in route waypoints), and `Mock: no` |
| Wi-Fi | A scan list of five access points: SWATCH-2.4G, xda2.4G, BLA_Store-2.4G, AP-8818, dftc-s. With a key, the resolved position is about 39.9137, 116.4051. |
| Cellular | Seven towers with signal strengths of -65, -71, -74, -78, -81, -83, and -86 dBm (see Known limitations) |
| GNSS | Stays at "Waiting for GNSS measurements" with no position. This is intended, because PolicyEngine suppresses GNSS. `adb logcat` shows `PE2: GNSS listener blocked`. |

Failure: the Wi-Fi tab shows only the emulator's own network (`AndroidWifi`, BSSID `00:13:10:85:fe:01`). That means the app is not in PolicyEngine's scope. Repeat the scope step and reboot.

**E3: rootless PolicyEngine (about 10 minutes).** Follow `Rootless-PolicyEngine/README.md` on an emulator or device (we used a separate AVD). It builds the module, installs LSPatch v1.2 (487) and Shizuku v13.5, patches the sample app with PolicyEngine embedded, and installs the result.
Success: the GPS tab shows spoofed coordinates. Wi-Fi and Cellular show real values, because rootless supports GPS only.
Failure: the GPS tab shows real coordinates, which means the patch was not applied.

**E2b (optional, about 30 minutes):** the same check as E2a on a physical rooted device (Android 14 or later, with Magisk and ReLSPosed). See `docs/EXPERIMENTS.md`.

## Repository layout

| Folder | Contents |
| :---- | :---- |
| PolicyEngine-ReLSPosed-Module/ | Rooted PolicyEngine (Kotlin, ReLSPosed module) |
| Rootless-PolicyEngine/ | Rootless PolicyEngine (LSPatch) |
| Rooted-Emulator-AVD/ | Configuration for a pre-configured rooted Android 16 AVD |
| SampleLocationStalk/ | Sample app retrieving location from all four geolocation channels |
| Analysis-Scripts/ | Dynamic per-app testing script |
| docs/ | Setup, experiment, and release documentation |

## Components

1. PolicyEngine (rooted). A ReLSPosed module written in Kotlin. Installable on a rooted Android device and managed through ReLSPosed.
2. Rootless PolicyEngine. The same defensive logic deployed through LSPatch, requiring no root. As reported in Table 7 (Appendix F), GPS spoofing is supported while system-server hooks are not, meaning Wi-Fi and cellular spoofing are unavailable in this configuration. The paper separately reports that the LSPatch build evaluated crashes on Android 16 (API 36) due to an ART runtime conflict (Section 9.1). This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5, which do not exhibit this crash on Android 16, an improvement over what is reported in the paper.
3. A pre-configured rooted Android 16 (API 36) AVD with Magisk, ReLSPosed, and PolicyEngine already installed and enabled. The AVD configuration is included in this repository; the disk image is hosted separately (see docs/SETUP.md) because of GitHub's file size limits.
4. SampleLocationStalk. An Android app showcasing a sample and harmless prototype of a permission-based stalkerware, that can be used to evaluate PolicyEngine.
5. A dynamic analysis script used to evaluate individual stalkerware apps, as described in the paper.

## Claims, components, and experiments

| Claim | Statement | Artifact component | Paper reference | Experiment |
| :---- | :---- | :---- | :---- | :---- |
| C1 | The artifact can dynamically analyze stalkerware apps, returning which geolocation channels and underlying API calls each app uses. | Analysis-Scripts/ | Section 3.2 | E1 |
| C2 | PolicyEngine, running rooted, spoofs GPS, Wi-Fi, and cellular location data and suppresses GNSS delivery to a target app. | PolicyEngine-ReLSPosed-Module/, Rooted-Emulator-AVD/ | Figures 2 and 3, Table 5, Sections 6 to 8 | E2 (via E2a or E2b) |
| C3 | PolicyEngine, running rootless via LSPatch, spoofs GPS location data. Wi-Fi and cellular spoofing are not yet supported in this configuration. | Rootless-PolicyEngine/ | Section 9.1, Appendix F | E3 |

Scripts and data behind each claim: C1 uses `Analysis-Scripts/artifact_automated_script_testing.ps1` and writes `pe_results.txt`. C2 uses the AVD with SampleLocationStalk as the target app, checked through the app's tab values and `adb logcat`. C3 uses the sample app patched as described in `Rootless-PolicyEngine/README.md`.

Badge mapping: Available is supported by this repository and its Zenodo archive, which includes the AVD disk image. Functional is supported by E1, E2a, and E3. Reproduced is supported by E2 on the sample app, a reduced-scale version of the paper's evaluation.

Full instructions for each experiment are in docs/EXPERIMENTS.md.

## Getting started

For E1, see Analysis-Scripts/README.md. An Android emulator or physical device connected through adb is required.

For E2, the recommended path is to load the pre-configured rooted AVD described in docs/SETUP.md, then follow docs/EXPERIMENTS.md. Component status should be checked through adb rather than by looking for application icons; see docs/KNOWN_LIMITATIONS.md for why.

For E3, see Rootless-PolicyEngine/README.md.

## Evaluation environment

The paper's primary results were obtained on a Google Pixel 8 running Android 16 (API 36), rooted with Magisk v30.7, using ReLSPosed v1.0.2 as the Zygisk instrumentation layer (Appendix A). The AVD provided with this artifact is a virtualized equivalent of that environment.

## Known limitations, nondeterminism, unavailable data, and reduced-scale alternatives

- **Nondeterminism.** Movement uses random speed variance and random pauses (a 15% chance at each waypoint), so exact values and timing differ between runs. The route itself is fixed. Without a Google Maps key, the position cycles through seven built-in waypoints.
- **Unavailable data and reduced-scale alternative.** The real-world stalkerware apps from the Coalition Against Stalkerware Threat List and the adversarial stalkerware prototype are not distributed, for licensing and ethical reasons (see docs/RELEASE_STATEMENT.md). The prototype is available to evaluators on request through HotCRP. E1 and E2 run on SampleLocationStalk, or on any APK you supply.
- **GNSS.** On an emulator the GNSS tab may show no data regardless of PolicyEngine, so use the `PE2: GNSS listener blocked` log line as evidence of suppression.
- **Cellular tab.** On this AVD the sample app lists the seven spoofed towers with their signal strengths, flags them as "unsupported type: CellInfoLte", and does not resolve a position. The seven signal values are the success criterion for this tab.
- **Manager icons.** The Magisk and ReLSPosed icons may be missing on the AVD. Verify with adb as shown above.
- **Sample app build.** It needs JDK 21 and Android Gradle Plugin 9.0.1, so older Android Studio versions cannot open it.

## Further documentation

See docs/RELEASE_STATEMENT.md for what is and is not publicly released, docs/REQUIREMENTS.md for hardware and software requirements, and docs/KNOWN_LIMITATIONS.md for limitations specific to the emulated environment.

## License

PolicyEngine builds on GPS Setter (https://github.com/jqssun/android-gps-setter), which is licensed under GPL-3.0, and is released under GPL-3.0.
