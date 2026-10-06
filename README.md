# PolicyEngine: A Defensive Framework Against Cross-Channel Location Validation in Android Stalkerware

This repository contains the artifact for the ACSAC 2026 submission. PolicyEngine is a Zygisk-based Android defensive framework that supplies geographically consistent synthetic location data to stalkerware and other location-monitoring applications across all four primary Android geolocation channels: GPS, Wi-Fi, cellular, and raw GNSS.

Badges requested: Available, Functional, Reproduced.

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
2. Rootless PolicyEngine. The same defensive logic deployed through LSPatch, requiring no root. As reported in Table 7 (Appendix F), GPS spoofing is supported while system-server hooks are not, meaning Wi-Fi and cellular spoofing are unavailable in this configuration. The paper separately reports that the LSPatch build evaluated crashes on Android 16 (API 36\) due to an ART runtime conflict (Section 9.1). This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5, which do not exhibit this crash on Android 16, an improvement over what is reported in the paper.  
3. A pre-configured rooted Android 16 (API 36\) AVD with Magisk, ReLSPosed, and PolicyEngine already installed and enabled. The AVD configuration is included in this repository; the disk image is hosted separately (see docs/SETUP.md) because of GitHub's file size limits.  
4. SampleLocationStalk. An Android app showcasing a sample and harmless prototype of a permission-based stalkerware, that can be used to evaluate PolicyEngine.
5. A dynamic analysis script used to evaluate individual stalkerware apps, as described in the paper.

## Claims, components, and experiments

| Claim | Statement | Artifact component | Paper reference | Experiment |
| :---- | :---- | :---- | :---- | :---- |
| C1 | The artifact can dynamically analyze stalkerware apps, returning which geolocation channels and underlying API calls each app uses. | Analysis-Scripts/ | Section 3.2 | E1 |
| C2 | PolicyEngine, running rooted, feeds synthetic data across GPS, Wi-Fi, and cellular while suppressing GNSS data across all four channels to a target app. | PolicyEngine-ReLSPosed-Module/, Rooted-Emulator-AVD/ | Figures 2 and 3, Table 5, Sections 6 to 8 | E2 (via E2a or E2b) |
| C3 | PolicyEngine, running rootless via LSPatch, spoofs GPS location data. Wi-Fi and cellular spoofing are not yet supported in this configuration. | Rootless-PolicyEngine/ | Section 9.1, Appendix F | E3 |

Full instructions for each experiment are in docs/EXPERIMENTS.md.

## Getting started

For E1, see Analysis-Scripts/README.md. An Android emulator or physical device connected through adb is required.

For E2, the recommended path is to load the pre-configured rooted AVD described in docs/SETUP.md, then follow docs/EXPERIMENTS.md. Component status should be checked through adb rather than by looking for application icons; see docs/KNOWN\_LIMITATIONS.md for why.

For E3, see Rootless-PolicyEngine/README.md.

## Evaluation environment

The paper's primary results were obtained on a Google Pixel 8 running Android 16 (API 36), rooted with Magisk v30.7, using ReLSPosed v1.0.2 as the Zygisk instrumentation layer (Appendix A). The AVD provided with this artifact is a virtualized equivalent of that environment.

## Further documentation

See docs/RELEASE\_STATEMENT.md for what is and is not publicly released, docs/REQUIREMENTS.md for hardware and software requirements, and docs/KNOWN\_LIMITATIONS.md for limitations specific to the emulated environment.  
