#!/usr/bin/env python3
"""
Computes:
  (1) N  = size of the qualifying corpus (SDK >= 23 AND more than 8 location APIs)
  (2) the distribution of location-CHANNEL combinations across the corpus

Usage:
    python3 corpus_stats.py apps.json
"""

import json
import sys
from collections import Counter

# Map each hook name to the channel it belongs to.
# Names taken verbatim from the "hooks_used" arrays in the JSON.
HOOK_TO_CHANNEL = {
    "LocationManager.requestLocationUpdates()":     "GPS",
    "LocationListener.onLocationChanged()":         "GPS",
    "Location.getSpeed()":                          "GPS",
    "Location.getBearing()":                        "GPS",
    "Location.getAltitude()":                       "GPS",
    "Location.getVerticalAccuracyMeters()":         "GPS",
    "WifiManager.getConnectionInfo()":              "WiFi",
    "WifiManager.getScanResults()":                 "WiFi",
    "TelephonyManager.getCellLocation()":           "Cell",
    "TelephonyManager.getAllCellInfo()":            "Cell",
    "GeofencingApi.addGeofences()":                 "Geofence",
    "PendingIntent Geofencing":                     "Geofence",
    "Accelerometer Events":                         "Sensor",
    "Gyroscope Events":                             "Sensor",
}

CHANNEL_ORDER = ["GPS", "WiFi", "Cell", "Geofence", "Sensor"]


def channel_signature(hooks):
    """Collapse a hook list into an ordered tuple of distinct channels."""
    chans = set()
    unknown = []
    for h in hooks:
        c = HOOK_TO_CHANNEL.get(h)
        if c:
            chans.add(c)
        else:
            unknown.append(h)
    ordered = sorted(
        chans,
        key=lambda c: CHANNEL_ORDER.index(c) if c in CHANNEL_ORDER else 99,
    )
    return tuple(ordered), unknown


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "apps.json"

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    apps = data.get("all_apps", [])

    # ---- sanity check -------------------------------------------------
    print("=" * 60)
    print("SANITY CHECK")
    print("=" * 60)
    print(f"Records in all_apps        : {len(apps)}")
    print(f"metadata says hooks corpus : "
          f"{data.get('metadata', {}).get('apps_with_location_hooks')}")
    print("(these two should match; if not, the file is partial)\n")

    # ---- (1) qualifying corpus N --------------------------------------
    sdk_ok      = [a for a in apps if (a.get("target_sdk") or 0) >= 23]
    hooks_ok    = [a for a in apps if (a.get("hook_count") or 0) > 8]
    qualifying  = [a for a in apps
                   if (a.get("target_sdk") or 0) >= 23 and (a.get("hook_count") or 0) > 8]

    print("=" * 60)
    print("QUALIFYING CORPUS (N)")
    print("=" * 60)
    print(f"SDK >= 23 only             : {len(sdk_ok)}")
    print(f"more than 8 hooks only     : {len(hooks_ok)}")
    print(f"BOTH  -->  N               : {len(qualifying)}   <-- use this number")
    print()

    # also report the stricter SDK >= 26 cut used in the extended batches
    q26 = [a for a in qualifying if (a.get("target_sdk") or 0) >= 26]
    print(f"of those, SDK >= 26        : {len(q26)}")
    print()

    # ---- (2) channel-combination distribution -------------------------
    def report(label, subset):
        counter = Counter()
        unknown_all = Counter()
        for a in subset:
            sig, unknown = channel_signature(a.get("hooks_used", []))
            counter[sig] += 1
            for u in unknown:
                unknown_all[u] += 1

        print("=" * 60)
        print(f"CHANNEL COMBINATIONS - {label}  (n={len(subset)})")
        print("=" * 60)
        if unknown_all:
            print("[!] hook names not in HOOK_TO_CHANNEL (add them):")
            for h, n in unknown_all.most_common():
                print(f"      {n:5d}  {h}")
            print()

        total = sum(counter.values())
        cum = 0
        for sig, n in counter.most_common():
            cum += n
            name = "+".join(sig) if sig else "(none)"
            print(f"{n:6d}  {100*n/total:5.1f}%   cum {100*cum/total:5.1f}%   {name}")
        print(f"\nDistinct combinations: {len(counter)}\n")
        return counter

    report("FULL CORPUS", apps)
    report("QUALIFYING (N)", qualifying)


if __name__ == "__main__":
    main()
