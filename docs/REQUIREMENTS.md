# Hardware and software requirements

## Hardware

E1 (dynamic analysis): an Android emulator or physical device connected through adb.

E2a (rooted, through the pre-configured AVD, recommended): an x86\_64 host with hardware virtualization exposed, either Intel VT-x/AMD-V or KVM on Linux, and the Android SDK emulator and command line tools installed. No GPU is required.

E2b (rooted, physical device, optional): a physical Android device running Android 14 or later, with Magisk and ReLSPosed installed. The paper's results were obtained on a Google Pixel 8, Android 16 (API 36), Magisk v30.7, ReLSPosed v1.0.2.

E3 (rootless): an Android emulator or physical device. No root is required. This artifact was verified working on Android 16 (API 36\) with LSPatch v1.2 (487) and Shizuku v13.5.

## Software

Python and its standard dependencies, the Android SDK, and the Kotlin toolchain.

WiGLE and OpenCelliD access-point and cell-tower data are hardcoded in the codebase and require no API key. This artifact ships without a live Google Maps API key, so trajectory generation uses a hardcoded fallback trajectory by default. No third-party API keys or accounts are required to run any experiment.

Optional, depending on which experiments are run: Magisk (for example v30.7), ReLSPosed (for example v1.0.2), LSPatch (v1.2 (487) or later), and Shizuku (v13.5 or later).

## Public infrastructure

ACSAC's guidelines ask that artifacts run on public research infrastructure when feasible. All three experiments in this artifact require an Android emulator or physical device, so none currently run on general-purpose public compute platforms such as Google Colab, which do not expose Android emulation. E2's rooted-emulator path additionally requires hardware virtualization access that is not available in the free tier of standard public Android emulator container images at Android 16 (API 36). The most widely used image for this purpose, budtmo/docker-android, caps its free public builds at API 34 (Android 14); support for Android 15 and 16 is offered only as a paid, sponsor-only feature ([https://github.com/budtmo/docker-android](https://github.com/budtmo/docker-android)). A custom container built from a generic base image could in principle target API 36, but would still require rooting the emulator inside a headless environment with no interactive interface for the Magisk installation process, together with nested KVM passthrough through the host operating system. Neither was achievable within the submission timeline. Consistent with ACSAC's provision for artifacts with special virtualization requirements, we provide the rooted environment as a downloadable, pre-configured AVD instead, with setup instructions in docs/SETUP.md.

## Expected runtime

E1: approximately 5 human-minutes and 2 compute-minutes for a single sample app, scaling to approximately 90 seconds per additional app.

E2a: approximately 10 human-minutes.

E2b, if run: approximately 30 human-minutes.

E3: approximately 10 human-minutes.

All experiments, individually and combined, fall well within ACSAC's one-day evaluation guideline.  
