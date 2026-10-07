# PolicyEngine: A Defensive Framework Against Cross-Channel Location Validation in Android Stalkerware

This repository contains the artifact for the ACSAC 2026 submission. PolicyEngine is a Zygisk-based Android defensive framework that supplies geographically consistent synthetic location data to stalkerware and other location-monitoring applications across all four primary Android geolocation channels: GPS, Wi-Fi, cellular, and raw GNSS.

Badges requested: Available, Functional, Reproduced.

Permanent archive (Available badge): https://doi.org/10.5281/zenodo.23174949. The record holds a snapshot of this repository and the rooted AVD disk image.

## Reviewer quick start

Start here. Each item points to the detailed document.

### Supported platforms and software versions

| Item | Version |
| :---- | :---- |
| Host OS | Windows with PowerShell (used for all testing). Linux and macOS were not tested. |
| Android SDK tools | `emulator` and `adb` on the PATH |
| AVD | `Rooted_Pixel_8_API_36`: Android 16 (API 36), google_apis, x86_64 |
| On the AVD | Magisk 30.6, ReLSPosed 1.0.2 (7211), PolicyEngine module 0.0.6 |
| Building PolicyEngine | JDK 17, Android Gradle Plugin 8.3.2, Kotlin 1.9.22 |
| Building the sample app | JDK 21, Android Gradle Plugin 9.0.1 (Android Studio Panda 2 or newer; see SampleLocationStalk/README.md) |
| Rootless (E3) | LSPatch v1.2 (487), Shizuku v13.5 |
| Paper results | Google Pixel 8, Android 16 (API 36), Magisk v30.7, ReLSPosed v1.0.2 |

### Requirements

| Requirement | Detail |
| :---- | :---- |
| Hardware | x86_64 host with hardware virtualization (Intel VT-x or AMD-V, or KVM on Linux). No special hardware. |
| Memory | The AVD's RAM size is set in `Rooted-Emulator-AVD/Rooted_Pixel_8_API_36.avd/config.ini` (`hw.ramSize`). |
| Disk | The disk image is about 2.7 GB. The AVD folder grows with use, so keep free space beyond that. |
| GPU | Not required. |
| GUI | Required (the emulator window and the sample app's tabs). |
| Network | Needed to download the disk image and build dependencies. PolicyEngine itself does not need it and falls back to a built-in route. The sample app's Wi-Fi and Cellular position lookups do need it. |
| API keys | None required. Optional: a Google Geolocation API key for the sample app (see SampleLocationStalk/README.md). Without it, the Wi-Fi and Cellular tabs still show scan data but not a resolved position. |
| Licensing | No paid or commercial software is needed. PolicyEngine builds on GPS Setter (https://github.com/jqssun/android-gps-setter, GPL-3.0) and is released under GPL-3.0. |

### Minimal check (about 10 minutes)

1. Download, install, and launch the AVD following docs/SETUP.md, Steps 1 to 3. Launch command: `emulator -avd Rooted_Pixel_8_API_36 -no-snapshot-load`
2. Verify the environment from a second terminal:
```
adb shell
su
magisk -v
ls /data/adb/modules
cat /data/adb/modules/zygisk_lsposed/module.prop
exit
exit
```
3. Open Google Maps in the emulator. With PolicyEngine active, it shows central Beijing (Dongcheng district).
4. Check PolicyEngine's log in PowerShell: `adb logcat -t 500 | Select-String "PE2: traj"` (on Linux or macOS: `adb logcat -d | grep "PE2: traj"`).

### Full evaluation (about 30 minutes, plus the 2.7 GB download)

- **E1:** copy an APK into `Analysis-Scripts\sample_apks`, then from the `Analysis-Scripts` folder run `.\artifact_automated_script_testing.ps1`. Details: Analysis-Scripts/README.md.
- **E2a:** install the sample app, add it to PolicyEngine's scope, and check its tabs. Steps: docs/EXPERIMENTS.md.
- **E3:** build, patch, and install as described in Rootless-PolicyEngine/README.md.

### Expected runtime

| Check | Time |
| :---- | :---- |
| Minimal check | about 10 minutes |
| Full evaluation (E1, E2a, E3) | about 30 minutes, plus the 2.7 GB download |
| Optional E2b (physical rooted device) | about 30 minutes |

### Expected outputs and success/failure 

The E2a rows below use the SampleLocationStalk as the target app.

| Check | Success | Failure |
| :---- | :---- | :---- |
| Minimal check | `magisk -v` prints a version, `zygisk_lsposed` is listed (ReLSPosed v1.0.2 (7211)), Maps is in Beijing, and `PE2: traj` lines appear with `idx` changing over time | No `PE2` lines or Maps elsewhere: reboot the AVD and check the module list |
| E1 | `pe_results.txt` gains a line for the tested app listing the channels and API calls it used | No line for the app |
| E2a, sample app GPS tab | Coordinates between 39.9125 and 39.9140 N and 116.4035 and 116.4065 E, and `Mock: no` | Coordinates outside this range |
| E2a, sample app Wi-Fi tab | Scan list of SWATCH-2.4G, xda2.4G, BLA_Store-2.4G, AP-8818, dftc-s | Only the emulator's own network (`AndroidWifi`): the app is not in PolicyEngine's scope |
| E2a, sample app Cellular tab | Seven towers listed with signal strengths of -65, -71, -74, -78, -81, -83, and -86 dBm  | Only one tower listed: the app is not in PolicyEngine's scope |
| E2a, sample app GNSS tab | Stays at "Waiting for GNSS measurements" (suppressed by design) | A satellite-derived position appears |
| E3 | GPS tab shows spoofed coordinates; Wi-Fi and cellular show real values | GPS tab shows real coordinates |

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
| C2 | PolicyEngine, running rooted, feeds synthetic data across GPS, Wi-Fi, and cellular while suppressing GNSS data to a target app. | PolicyEngine-ReLSPosed-Module/, Rooted-Emulator-AVD/ | Figures 2 and 3, Table 5, Sections 6 to 8 | E2 (via E2a or E2b) |
| C3 | PolicyEngine, running rootless via LSPatch, spoofs GPS location data. Wi-Fi and cellular spoofing are not yet supported in this configuration. | Rootless-PolicyEngine/ | Section 9.1, Appendix F | E3 |

Scripts and data behind each claim: C1 uses `Analysis-Scripts/artifact_automated_script_testing.ps1` and writes `pe_results.txt`. C2 uses the AVD (docs/SETUP.md) with SampleLocationStalk/ as the target app, checked through the app's tabs and `adb logcat`. C3 uses the patched app described in `Rootless-PolicyEngine/README.md`.

Badge mapping: Available is supported by this repository and its Zenodo archive, which includes the AVD disk image. Functional is supported by E1, E2a, and E3. Reproduced is supported by E2, run on the sample app as a reduced-scale version of the paper's evaluation.

Full instructions for each experiment are in docs/EXPERIMENTS.md.

## Getting started

For E1, see Analysis-Scripts/README.md. An Android emulator or physical device connected through adb is required.

For E2, the recommended path is to load the pre-configured rooted AVD described in docs/SETUP.md, then follow docs/EXPERIMENTS.md. Component status should be checked through adb rather than by looking for application icons; see docs/KNOWN_LIMITATIONS.md for why.

For E3, see Rootless-PolicyEngine/README.md.

## Evaluation environment

The paper's primary results were obtained on a Google Pixel 8 running Android 16 (API 36), rooted with Magisk v30.7, using ReLSPosed v1.0.2 as the Zygisk instrumentation layer (Appendix A). The AVD provided with this artifact is a virtualized equivalent of that environment.

## Known limitations, nondeterminism, unavailable data, and reduced-scale alternatives

- **Nondeterminism.** Speed varies randomly and the device pauses at random waypoints (a 15% chance at each), so exact values and timing differ between runs. The route itself is fixed.
- **Unavailable data and reduced-scale alternative.** The real-world stalkerware apps from the Coalition Against Stalkerware Threat List and the adversarial stalkerware prototype are not distributed (see docs/RELEASE_STATEMENT.md). The prototype is available to evaluators on request through HotCRP. E1 and E2 run on SampleLocationStalk, or on any APK you supply.
- **Essential and optional resources.** E2a (claim C2 and the Functional badge) needs an x86_64 host with hardware virtualization. A physical rooted device (E2b) and a Geolocation API key are optional.
- **Sample app tabs.** On the emulator, the Cellular tab lists the seven spoofed towers with their signal strengths but does not show a position in Beijing. The Connected AP line on the Wi-Fi tab may show the emulator's own network (`AndroidWifi`), so judge by the scan list below it.

Other limitations are in docs/KNOWN_LIMITATIONS.md.

## Further documentation

See docs/RELEASE_STATEMENT.md for what is and is not publicly released, docs/REQUIREMENTS.md for hardware and software requirements, and docs/KNOWN_LIMITATIONS.md for limitations specific to the emulated environment.
