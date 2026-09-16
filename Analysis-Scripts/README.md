# Analysis script

This directory contains the script used to evaluate individual stalkerware apps, as described in Section 3.2 of the paper.

## artifact\_automated\_script\_testing.ps1

Installs an APK through adb, observes it for 60 seconds to capture location API activity through dumpsys and logcat, then uninstalls it. Reports which geolocation channels and underlying API calls the app invokes. Requires an Android emulator or physical device connected through adb.

Usage:

.\\artifact\_automated\_script\_testing.ps1

By default the script reads APKs from .\\sample\_apks and writes results to .\\pe\_results.txt. Both paths, along with the number of apps to test, can be overridden with the \-apkFolder, \-logFile, and \-maxApps parameters.

See ../docs/EXPERIMENTS.md for the full E1 walkthrough and expected output.  
