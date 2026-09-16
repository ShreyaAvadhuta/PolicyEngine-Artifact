package com.example.samplelocationstalk;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab 3 — Wi-Fi positioning.
 *
 * Key methods:
 *   WifiManager.startScan()           — triggers a scan of nearby APs
 *   WifiManager.getScanResults()      — retrieves the list of discovered APs
 *   WifiManager.getConnectionInfo()   — returns the currently connected AP
 *   Google Geolocation API            — resolves AP BSSIDs + signal to lat/lng
 */
public class WifiFragment extends Fragment {

    private static final String TAG = "WifiFragment";
    private static final String GOOGLE_API_KEY = "YOUR_GOOGLE_GEOLOCATION_API_KEY";
    private static final String GOOGLE_GEOLOCATION_URL =
            "https://www.googleapis.com/geolocation/v1/geolocate?key=" + GOOGLE_API_KEY;
    private static final int MAX_APS = 8;

    private TextView tvStatus, tvLatLng, tvDetails;
    private WifiManager wifiManager;
    private BroadcastReceiver scanReceiver;
    private boolean receiverRegistered;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle saved) {
        View v = inflater.inflate(R.layout.fragment_location_tab, container, false);
        tvStatus  = v.findViewById(R.id.tvStatus);
        tvLatLng  = v.findViewById(R.id.tvLatLng);
        tvDetails = v.findViewById(R.id.tvDetails);
        return v;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onResume() {
        super.onResume();
        wifiManager = (WifiManager)
                requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        tvStatus.setText("Scanning Wi-Fi APs...");
        showConnectionInfo();
        startScan();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @SuppressLint("MissingPermission")
    private void showConnectionInfo() {
        if (wifiManager == null) return;
        WifiInfo info = wifiManager.getConnectionInfo();
        if (info != null && info.getBSSID() != null) {
            tvDetails.setText(String.format(Locale.US,
                    "Connected AP:\n  SSID: %s\n  BSSID: %s\n  RSSI: %d dBm\n  Freq: %d MHz\n  Link speed: %d Mbps\n\n",
                    info.getSSID(), info.getBSSID(), info.getRssi(),
                    info.getFrequency(), info.getLinkSpeed()));
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            tvStatus.setText("Wi-Fi disabled");
            return;
        }

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                unregisterReceiver();
                processScanResults();
            }
        };
        requireContext().registerReceiver(scanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        receiverRegistered = true;

        if (!wifiManager.startScan()) {
            unregisterReceiver();
            processScanResults();
        }
    }

    private void unregisterReceiver() {
        if (receiverRegistered && scanReceiver != null) {
            try { requireContext().unregisterReceiver(scanReceiver); }
            catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void processScanResults() {
        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
        } catch (SecurityException e) {
            results = Collections.emptyList();
        }
        if (results == null || results.isEmpty()) {
            tvStatus.setText("No APs found");
            return;
        }

        List<ScanResult> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Integer.compare(b.level, a.level));
        List<ScanResult> top = sorted.subList(0, Math.min(MAX_APS, sorted.size()));

        // Show the scanned APs
        StringBuilder sb = new StringBuilder();
        if (tvDetails.getText().length() > 0)
            sb.append(tvDetails.getText()).append("\n");
        sb.append(String.format(Locale.US, "Scan results: %d APs (showing top %d)\n\n",
                results.size(), top.size()));
        for (ScanResult ap : top) {
            sb.append(String.format(Locale.US, "  %-20s  %s  %4d dBm  %d MHz\n",
                    ap.SSID, ap.BSSID, ap.level, ap.frequency));
        }
        tvStatus.setText("Resolving position via Google Geolocation API...");
        tvDetails.setText(sb.toString());

        executor.execute(() -> queryGoogleGeolocation(top));
    }

    private void queryGoogleGeolocation(List<ScanResult> aps) {
        try {
            JSONArray wifiArray = new JSONArray();
            for (ScanResult ap : aps) {
                JSONObject entry = new JSONObject();
                entry.put("macAddress", ap.BSSID);
                entry.put("signalStrength", ap.level);
                entry.put("channel", ap.frequency);
                wifiArray.put(entry);
            }
            JSONObject body = new JSONObject();
            body.put("wifiAccessPoints", wifiArray);

            HttpURLConnection conn = (HttpURLConnection)
                    new URL(GOOGLE_GEOLOCATION_URL).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = readStream(conn.getErrorStream());
                Log.e(TAG, "Google API error " + code + ": " + err);
                post(() -> tvStatus.setText("Google API error: " + code));
                return;
            }

            String respBody = readStream(conn.getInputStream());
            JSONObject resp = new JSONObject(respBody);
            JSONObject loc  = resp.getJSONObject("location");
            double lat      = loc.getDouble("lat");
            double lng      = loc.getDouble("lng");
            double accuracy = resp.optDouble("accuracy", Double.NaN);

            post(() -> {
                tvStatus.setText(String.format(Locale.US,
                        "Wi-Fi position (from %d APs, accuracy +/-%dm)",
                        aps.size(), (int) accuracy));
                tvLatLng.setText(String.format(Locale.US, "%.6f, %.6f", lat, lng));
            });

        } catch (Exception e) {
            Log.e(TAG, "Wi-Fi geolocation failed: " + e.getMessage());
            post(() -> tvStatus.setText("Error: " + e.getMessage()));
        }
    }

    private String readStream(java.io.InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (Scanner s = new Scanner(is)) {
            while (s.hasNextLine()) sb.append(s.nextLine());
        }
        return sb.toString();
    }

    private void post(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }
}
