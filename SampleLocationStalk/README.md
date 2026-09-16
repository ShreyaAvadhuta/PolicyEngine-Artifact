# Sample Location Stalk

This directory contains a standalone Android app that exemplifies how a permission-based stalkerware could retrieve the user's location from the four primary Android geolocation channels, corresponding to the channels evaluated in Section 3.2 and Table 3 of the paper. Each channel is presented in its own tab, showing (1) the resolved position at the top and (2) every API method called with its actual return value, illustrating how raw data flows into the final position fix.

## Contents

The app has four tabs, one per geolocation channel:

| Tab | Location source | Key Android APIs |
|-----|----------------|-----------------|
| **GNSS** | Raw GNSS pseudoranges, solved with a Weighted Least Squares engine | `GnssMeasurementsEvent.Callback`, `GnssNavigationMessage.Callback`, WLS solver from [gps-measurement-tools](https://github.com/google/gps-measurement-tools) |
| **GPS** | Standard `LocationManager` GPS provider | `LocationManager.requestLocationUpdates(GPS_PROVIDER, ...)`, `Location.getLatitude/getLongitude/getAccuracy/getAltitude/getSpeed/getBearing/isFromMockProvider` |
| **Wi-Fi** | Nearby access-point scan, resolved through the Google Geolocation API | `WifiManager.getConnectionInfo()`, `WifiManager.startScan()`, `WifiManager.getScanResults()` |
| **Cellular** | Visible cell towers, resolved through the Google Geolocation API | `TelephonyManager.requestCellInfoUpdate()`, `TelephonyManager.getAllCellInfo()`, `CellIdentityLte.getMcc/getMnc/getTac/getCi` |

### Source layout

- `app/src/main/java/com/example/samplelocationstalk/` &mdash; `MainActivity`, `LocationPagerAdapter`, and the four tab fragments (`GnssFragment`, `GpsFragment`, `WifiFragment`, `CellularFragment`).
- `app/src/main/java/pseudorange/` &mdash; WLS GNSS solver, adapted from the `GNSSLogger` project within Google's [gps-measurement-tools](https://github.com/google/gps-measurement-tools).
- `app/libs/` &mdash; prebuilt JARs for ASN.1 SUPL decoding, protocol buffers, and math utilities, used by the GNSS solver.

## Building and Installing

Requires the Android SDK (compileSdk 36) and Java 21.

Before building, create a `local.properties` file with the path to your Android SDK:

```
sdk.dir=/path/to/your/Android/Sdk
```

The app requests three runtime permissions on first launch: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `READ_PHONE_STATE`. All four tabs require location permissions; the Cellular tab additionally requires `READ_PHONE_STATE`.

## Google Geolocation API key

The Wi-Fi and Cellular tabs resolve their raw observations (access-point BSSIDs or cell-tower identities) into a latitude/longitude position by calling the [Google Geolocation API](https://developers.google.com/maps/documentation/geolocation/overview). This requires an API key.

Before building, replace the placeholder in `WifiFragment.java` and `CellularFragment.java`:

```java
private static final String GOOGLE_API_KEY = "YOUR_GOOGLE_GEOLOCATION_API_KEY";
```

To obtain a key, create a project in the [Google Cloud Console](https://console.cloud.google.com/), enable the **Geolocation API**, and generate an API key. The free tier (documented at the link above) is sufficient for testing. Without a valid key, the Wi-Fi and Cellular tabs will still display raw scan data but will not resolve a position.

## Requirements

Android 8.1 (API 27) or later. A physical device is recommended for the GNSS tab, as emulators do not produce real pseudorange measurements.