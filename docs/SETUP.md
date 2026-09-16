# Setup: the pre-configured rooted Android 16 AVD

This document explains how to obtain and launch the pre-configured Android Virtual Device (AVD) that accompanies this artifact. The AVD runs Android 16 (API 36\) and has Magisk, ReLSPosed v1.0.2, and PolicyEngine already installed and enabled, matching the environment described in Appendix A of the paper.

## Requirements

An x86\_64 host with hardware virtualization exposed (Intel VT-x/AMD-V on Windows or macOS, KVM on Linux). No GPU is required.

The Android SDK command line tools, specifically emulator and adb. These are included with Android Studio and can also be installed independently through sdkmanager.

The Android 16 (API 36\) x86\_64 google\_apis system image (system-images;android-36;google\_apis;x86\_64).

## Step 1: download the disk image

The AVD's configuration files are included in this repository under Rooted-Emulator-AVD/. The disk image itself is approximately 2.7 GB, which exceeds GitHub's file size limits, so it is hosted separately.

Download link: [https://drive.google.com/file/d/1S0i1unnn8kxtOpWJSI4Iyz9v3MsFt8Ml/view?usp=sharing](https://drive.google.com/file/d/1S0i1unnn8kxtOpWJSI4Iyz9v3MsFt8Ml/view?usp=sharing)

Place the downloaded file, userdata-qemu.img.qcow2, into:

Rooted-Emulator-AVD/Rooted\_Pixel\_8\_API\_36.avd/userdata-qemu.img.qcow2

## Step 2: install the AVD

Copy the complete Rooted\_Pixel\_8\_API\_36.avd directory and the Rooted\_Pixel\_8\_API\_36.ini file into your Android AVD directory. On Linux and macOS this is normally \~/.android/avd/. On Windows it is normally %USERPROFILE%.android\\avd.

Open Rooted\_Pixel\_8\_API\_36.ini and check that the path line points to the correct location of the AVD folder on your machine. Update it if it does not.

## Step 3: launch the AVD

Through Android Studio, open Device Manager and run Rooted\_Pixel\_8\_API\_36.

From the command line:

emulator \-avd Rooted\_Pixel\_8\_API\_36 \-no-snapshot-load

## Step 4: verify the environment

The Magisk and ReLSPosed manager applications do not reliably keep a visible launcher icon in this environment. This is expected and does not indicate a failure; see docs/KNOWN\_LIMITATIONS.md for an explanation. Verify the environment through adb instead:

adb shell

su

magisk \-v

ls /data/adb/modules

cat /data/adb/modules/zygisk\_lsposed/module.prop

A correctly configured AVD will show a running Magisk daemon version, list zygisk\_lsposed among the installed modules, and report name=ReLSPosed, version=v1.0.2 (7211).

Once the environment is verified, proceed to docs/EXPERIMENTS.md.  
