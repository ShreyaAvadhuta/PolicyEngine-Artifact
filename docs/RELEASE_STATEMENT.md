# Public release statement

This document states which parts of the artifact are publicly released and which are not, as required by ACSAC's artifact evaluation guidelines.

## Publicly released

The following materials are publicly released in this repository:

PolicyEngine source code, in both the rooted (ReLSPosed) and rootless (LSPatch) configurations.

The pre-configured rooted Android 16 (API 36\) AVD. The configuration files are included in this repository; the disk image is hosted separately on Google Drive because of GitHub's file size limits, and is linked from docs/SETUP.md.

The dynamic analysis script used to evaluate individual stalkerware apps, as described in Section 3.2 of the paper.

## Not publicly released

The adversarial stalkerware prototype described in the paper is not publicly released. Consistent with the Ethics Considerations section of the paper, we are withholding this material to avoid distributing a directly deployable stalkerware proof-of-concept. It is available to ACSAC artifact evaluators on request through HotCRP for independent evaluation.

The real-world stalkerware applications referenced in the paper's corpus are third-party material drawn from the Coalition Against Stalkerware Threat List and are not redistributed in any form, consistent with the licensing terms of that source.

## API keys

This artifact ships without a live Google Maps API key. WiGLE and OpenCelliD data are hardcoded in the codebase and require no key under any configuration. See docs/KNOWN\_LIMITATIONS.md.

## Permanent storage

This GitHub repository serves as the artifact's storage for the submission and evaluation period. Consistent with the requirements of the Available badge, we commit to depositing the final version of this artifact in a permanent, DOI-backed repository, such as Zenodo, by the camera-ready deadline of October 21\.  
