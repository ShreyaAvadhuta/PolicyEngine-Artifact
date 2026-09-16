# Known limitations

This document covers two kinds of items: limitations specific to the emulated evaluation environment provided with this artifact, and one design characteristic of PolicyEngine itself that evaluators should be aware of while testing. Neither affects the results reported in the paper, which were obtained on physical hardware, as described in Appendix A.

## Manager application icons

The Magisk and ReLSPosed manager applications may not keep a visible launcher icon in the pre-configured AVD, even though root and the ReLSPosed module are both active. This is a consequence of how the AVD was rooted: through a non-standard, adb-based patching process rather than Magisk's normal in-application boot partition patch. The underlying root daemon and the ReLSPosed module operate independently of whether the manager applications' icons are visible. To verify the environment, use the adb commands described in docs/SETUP.md rather than searching for application icons.

## Live trajectory fetching

This artifact ships without a live Google Maps API key. Trajectory generation therefore uses a hardcoded fallback trajectory by default. This is expected behavior, not an error, and requires no action from the evaluator. The fallback trajectory still produces geographically consistent, correctly located synthetic data, confirmed by third-party applications correctly displaying the intended location. An evaluator who wishes to test live route fetching can supply their own Google Maps Directions and Elevation API key in place of the YOUR\_GOOGLE\_MAPS\_API\_KEY placeholder in LocationHook.kt.

WiGLE and OpenCelliD access-point and cell-tower data are hardcoded in the codebase and do not require an API key under any configuration.

## Rootless configuration

The paper (Section 9.1) reports that the LSPatch build evaluated crashes on Android 16 (API 36), caused by a conflict between LSPatch's hooking mechanism and ART's ProfileSaver on that Android version. This artifact was verified working with LSPatch v1.2 (487) and Shizuku v13.5, which do not exhibit that crash on Android 16; this is an improvement made after the paper's evaluation and is not itself documented in the paper. As reported in Table 7 (Appendix F), GPS spoofing is supported in the rootless configuration while system-server hooks are not, so Wi-Fi and cellular spoofing remain unavailable regardless of Android version, since they require system-server instrumentation that application-level patching cannot provide. This matches the claim made in C3.

## Disk image hosting

The rooted AVD's disk image is approximately 2.7 GB, which exceeds GitHub's per-file size limit. It is therefore hosted on Google Drive rather than included directly in this repository. See docs/SETUP.md for the download link and installation instructions.

## GNSS channel behaves differently from the other three

As shown in Figure 2 of the paper, PolicyEngine's action for raw GNSS is to suppress listener registrations rather than deliver synthetic values, unlike GPS, Wi-Fi, and cellular, which receive spoofed data consistent with the trajectory. An evaluator checking GNSS output should expect it to return nothing (listener calls blocked) rather than a synthetic Beijing coordinate. This is the intended design, not a defect: forging raw GNSS measurements that resolve to specific coordinates would require a real-time GNSS positioning engine, which the paper describes as an open problem (Section 9.3), not something this artifact implements.  
