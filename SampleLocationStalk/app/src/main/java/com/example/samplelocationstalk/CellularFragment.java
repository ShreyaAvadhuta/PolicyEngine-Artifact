package com.example.samplelocationstalk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab 4 — Cellular tower positioning.
 *
 * Key methods:
 *   TelephonyManager.requestCellInfoUpdate()  — fresh cell tower query (API 29+)
 *   TelephonyManager.getAllCellInfo()          — cached cell tower list
 *   CellInfoLte / CellInfoWcdma / CellInfoGsm — parsed tower identity
 *   Google Geolocation API                    — resolves MCC/MNC/LAC/CID to lat/lng
 */
public class CellularFragment extends Fragment {

    private static final String TAG = "CellularFragment";
    private static final String GOOGLE_API_KEY = "YOUR_GOOGLE_GEOLOCATION_API_KEY";
    private static final String GOOGLE_GEOLOCATION_URL =
            "https://www.googleapis.com/geolocation/v1/geolocate?key=" + GOOGLE_API_KEY;

    private TextView tvStatus, tvLatLng, tvDetails;
    private TelephonyManager telephonyManager;
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
        telephonyManager = (TelephonyManager)
                requireContext().getSystemService(Context.TELEPHONY_SERVICE);
        tvStatus.setText("Reading cell towers...");
        requestCellInfo();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @SuppressLint("MissingPermission")
    private void requestCellInfo() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setText("READ_PHONE_STATE not granted");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.requestCellInfoUpdate(executor,
                    new TelephonyManager.CellInfoCallback() {
                        @Override
                        public void onCellInfo(@NonNull List<CellInfo> list) {
                            processCellInfoList(list);
                        }
                        @Override
                        public void onError(int code, @Nullable Throwable t) {
                            // Fall back to cached
                            List<CellInfo> cached = getCachedCellInfo();
                            if (cached != null && !cached.isEmpty()) {
                                processCellInfoList(cached);
                            } else {
                                post(() -> tvStatus.setText("requestCellInfoUpdate error=" + code));
                            }
                        }
                    });
        } else {
            List<CellInfo> cached = getCachedCellInfo();
            if (cached != null && !cached.isEmpty()) {
                processCellInfoList(cached);
            } else {
                tvStatus.setText("No cell info available");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private List<CellInfo> getCachedCellInfo() {
        try { return telephonyManager.getAllCellInfo(); }
        catch (SecurityException e) { return null; }
    }

    private void processCellInfoList(List<CellInfo> list) {
        if (list == null || list.isEmpty()) {
            post(() -> tvStatus.setText("No cell towers found"));
            return;
        }

        // Sort by signal strength
        List<CellInfo> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(getDbm(b), getDbm(a)));

        // Show all visible towers
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Visible towers: %d\n\n", sorted.size()));

        CellTower best = null;

        for (CellInfo info : sorted) {
            CellTower tower = parseTower(info);
            if (tower != null) {
                if (best == null) best = tower;
                sb.append(String.format(Locale.US,
                        "  %s  MCC=%d MNC=%d LAC=%d CID=%d  %d dBm\n",
                        tower.radio, tower.mcc, tower.mnc, tower.lac, tower.cid, tower.signalDbm));
            } else {
                sb.append(String.format(Locale.US,
                        "  (unsupported type: %s  %d dBm)\n",
                        info.getClass().getSimpleName(), getDbm(info)));
            }
        }

        CellTower toResolve = best;
        post(() -> tvDetails.setText(sb.toString()));

        if (toResolve != null) {
            post(() -> tvStatus.setText("Resolving strongest tower via Google Geolocation API..."));
            executor.execute(() -> queryGoogleGeolocation(toResolve));
        } else {
            post(() -> tvStatus.setText("No usable cell tower identity"));
        }
    }

    private void queryGoogleGeolocation(CellTower cell) {
        try {
            JSONObject cellObj = new JSONObject();
            cellObj.put("cellId", cell.cid);
            cellObj.put("locationAreaCode", cell.lac);
            cellObj.put("mobileCountryCode", cell.mcc);
            cellObj.put("mobileNetworkCode", cell.mnc);
            cellObj.put("signalStrength", cell.signalDbm);

            org.json.JSONArray cellArray = new org.json.JSONArray();
            cellArray.put(cellObj);

            JSONObject body = new JSONObject();
            body.put("cellTowers", cellArray);

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
                        "Cell position (%s MCC=%d MNC=%d CID=%d, accuracy +/-%dm)",
                        cell.radio, cell.mcc, cell.mnc, cell.cid, (int) accuracy));
                tvLatLng.setText(String.format(Locale.US, "%.6f, %.6f", lat, lng));
            });

        } catch (Exception e) {
            Log.e(TAG, "Cell geolocation failed: " + e.getMessage());
            post(() -> tvStatus.setText("Error: " + e.getMessage()));
        }
    }

    // --- Cell tower parsing helpers ---

    private static class CellTower {
        final String radio;
        final int mcc, mnc, lac, cid, signalDbm;
        CellTower(String radio, int mcc, int mnc, int lac, int cid, int signalDbm) {
            this.radio = radio; this.mcc = mcc; this.mnc = mnc;
            this.lac = lac; this.cid = cid; this.signalDbm = signalDbm;
        }
    }

    private CellTower parseTower(CellInfo info) {
        if (info instanceof CellInfoLte) {
            CellIdentityLte id = ((CellInfoLte) info).getCellIdentity();
            int mcc = mcc(id), mnc = mnc(id);
            if (valid(mcc) && valid(id.getCi()))
                return new CellTower("LTE", mcc, mnc, id.getTac(), id.getCi(),
                        ((CellInfoLte) info).getCellSignalStrength().getDbm());
        } else if (info instanceof CellInfoWcdma) {
            CellIdentityWcdma id = ((CellInfoWcdma) info).getCellIdentity();
            int mcc = mcc(id), mnc = mnc(id);
            if (valid(mcc) && valid(id.getCid()))
                return new CellTower("UMTS", mcc, mnc, id.getLac(), id.getCid(),
                        ((CellInfoWcdma) info).getCellSignalStrength().getDbm());
        } else if (info instanceof CellInfoGsm) {
            CellIdentityGsm id = ((CellInfoGsm) info).getCellIdentity();
            int mcc = mcc(id), mnc = mnc(id);
            if (valid(mcc) && valid(id.getCid()))
                return new CellTower("GSM", mcc, mnc, id.getLac(), id.getCid(),
                        ((CellInfoGsm) info).getCellSignalStrength().getDbm());
        }
        return null;
    }

    private int getDbm(CellInfo info) {
        if (info instanceof CellInfoLte)   return ((CellInfoLte) info).getCellSignalStrength().getDbm();
        if (info instanceof CellInfoWcdma) return ((CellInfoWcdma) info).getCellSignalStrength().getDbm();
        if (info instanceof CellInfoGsm)   return ((CellInfoGsm) info).getCellSignalStrength().getDbm();
        return -999;
    }

    private int mcc(CellIdentityLte id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && id.getMccString() != null)
            try { return Integer.parseInt(id.getMccString()); } catch (NumberFormatException e) {}
        return id.getMcc();
    }
    private int mnc(CellIdentityLte id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && id.getMncString() != null)
            try { return Integer.parseInt(id.getMncString()); } catch (NumberFormatException e) {}
        return id.getMnc();
    }
    private int mcc(CellIdentityWcdma id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && id.getMccString() != null)
            try { return Integer.parseInt(id.getMccString()); } catch (NumberFormatException e) {}
        return id.getMcc();
    }
    private int mnc(CellIdentityWcdma id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && id.getMncString() != null)
            try { return Integer.parseInt(id.getMncString()); } catch (NumberFormatException e) {}
        return id.getMnc();
    }
    private int mcc(CellIdentityGsm id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && id.getMccString() != null)
            try { return Integer.parseInt(id.getMccString()); } catch (NumberFormatException e) {}
        return id.getMcc();
    }
    private int mnc(CellIdentityGsm id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && id.getMncString() != null)
            try { return Integer.parseInt(id.getMncString()); } catch (NumberFormatException e) {}
        return id.getMnc();
    }
    private boolean valid(int v) { return v != Integer.MAX_VALUE && v > 0; }

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
