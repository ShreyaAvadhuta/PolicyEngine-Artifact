# PolicyEngine, rootless through LSPatch

This directory contains the rootless deployment of PolicyEngine, evaluated in Section 9.1 of the paper. It runs by patching a target APK with LSPatch ([https://github.com/JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch)), and does not require Zygisk or device root. Shizuku supplies the elevated permissions LSPatch needs, granted through adb.

## Coverage

As reported in Table 7 (Appendix F) of the paper, GPS location spoofing is supported in this configuration, while system-server hooks are not.

The paper (Section 9.1) reports that the LSPatch build evaluated (v0.6) crashes on Android 16 (API 36\) due to a conflict between LSPatch's hooking mechanism and ART's ProfileSaver. This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5, which do not exhibit that crash on Android 16; this improvement postdates the paper's evaluation and is not itself documented in it.

Wi-Fi SSID and BSSID spoofing is not supported in this configuration, because it requires access to hidden system fields that are unavailable without system-server instrumentation.

Cellular spoofing is not yet supported in this configuration and remains in progress.

## Installing

This experiment combines three separate pieces: PolicyEngine itself (built from this directory), the sample stalkerware app (the target being patched), and LSPatch (the tool that embeds one into the other). The steps below combine them.

Step 1\. Build the foss flavor of the PolicyEngine module APK from this directory's app/ folder, using the same command as PolicyEngine-ReLSPosed-Module/ (./gradlew assembleFossDebug; see ../PolicyEngine-ReLSPosed-Module/README.md for the output path). This produces the module APK to be embedded into the target app.

Step 2\. Install LSPatch's manager app ([https://github.com/JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch)) and Shizuku ([https://github.com/RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)) on the target device or emulator. This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5. Start Shizuku to grant the elevated permissions LSPatch requires.

Step 3\. In the LSPatch manager app, select the sample stalkerware app as the target to patch, select the PolicyEngine module APK built in Step 1 to embed, and start the patch. Once patching finishes, the manager presents two options: "Export APK" or "Install",  Tap "Install" to complete installation directly from the manager.

See ../docs/EXPERIMENTS.md for the full E3 walkthrough and expected output.  
