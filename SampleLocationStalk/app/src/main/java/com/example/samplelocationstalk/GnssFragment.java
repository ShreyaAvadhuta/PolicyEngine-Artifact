package com.example.samplelocationstalk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.location.lbs.gnss.gps.pseudorange.GpsNavigationMessageStore;
import com.google.location.lbs.gnss.gps.pseudorange.PseudorangePositionVelocityFromRealTimeEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab 1 — Raw GNSS pseudorange measurements fed into a Weighted Least Squares
 * solver to compute a position independently from the platform GPS provider.
 *
 * Key methods:
 *   registerGnssMeasurementsCallback    — raw pseudorange observables
 *   registerGnssNavigationMessageCallback — ephemeris / navigation data
 *   PseudorangePositionVelocityFromRealTimeEvents.computePositionVelocitySolutionsFromRawMeas
 */
public class GnssFragment extends Fragment {

    private static final String TAG = "GnssFragment";

    private TextView tvStatus, tvLatLng, tvDetails;
    private LocationManager locationManager;

    private GnssMeasurementsEvent.Callback measCallback;
    private GnssNavigationMessage.Callback navCallback;
    private LocationListener refLocationListener;

    private PseudorangePositionVelocityFromRealTimeEvents wlsEngine;
    private GpsNavigationMessageStore navStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

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
        locationManager = (LocationManager)
                requireContext().getSystemService(Context.LOCATION_SERVICE);

        wlsEngine = new PseudorangePositionVelocityFromRealTimeEvents();
        navStore  = new GpsNavigationMessageStore();

        tvStatus.setText("Waiting for GNSS measurements...");

        // The WLS engine needs a rough reference position to bootstrap.
        // We feed it from a lightweight GPS listener.
        refLocationListener = location -> {
            wlsEngine.setReferencePosition(
                    (int) (location.getLatitude()  * 1e7),
                    (int) (location.getLongitude() * 1e7),
                    (int) (location.getAltitude()  * 1e7));
        };
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 10_000L, 0f,
                refLocationListener, Looper.getMainLooper());

        // Register for raw GNSS measurement events
        measCallback = new GnssMeasurementsEvent.Callback() {
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                List<GnssMeasurement> snapshot = new ArrayList<>(event.getMeasurements());
                executor.execute(() -> solveWls(event, snapshot));
            }
        };
        locationManager.registerGnssMeasurementsCallback(measCallback,
                new Handler(Looper.getMainLooper()));

        // Register for navigation messages (ephemeris data for the solver)
        navCallback = new GnssNavigationMessage.Callback() {
            @Override
            public void onGnssNavigationMessageReceived(GnssNavigationMessage msg) {
                byte prn  = (byte) msg.getSvid();
                byte type = (byte) (msg.getType() >> 8);
                short sub = (short) msg.getSubmessageId();
                byte[] data = msg.getData();
                executor.execute(() -> {
                    try {
                        if (type == 1) navStore.onNavMessageReported(prn, type, sub, data);
                    } catch (Exception e) {
                        Log.e(TAG, "Nav msg error: " + e.getMessage());
                    }
                });
            }
        };
        locationManager.registerGnssNavigationMessageCallback(navCallback,
                new Handler(Looper.getMainLooper()));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationManager != null) {
            locationManager.unregisterGnssMeasurementsCallback(measCallback);
            locationManager.unregisterGnssNavigationMessageCallback(navCallback);
            if (refLocationListener != null)
                locationManager.removeUpdates(refLocationListener);
        }
        executor.shutdownNow();
    }

    private void solveWls(GnssMeasurementsEvent event, List<GnssMeasurement> measurements) {
        try {
            wlsEngine.computePositionVelocitySolutionsFromRawMeas(event);
            double[] sol = wlsEngine.getPositionSolutionLatLngDeg();

            if (sol == null) {
                uiHandler.post(() -> {
                    tvStatus.setText("Converging... (" + measurements.size() + " SVs)");
                    showSatelliteDetails(measurements);
                });
                return;
            }

            double lat = sol[0];
            double lng = sol[1];
            double alt = sol[2];

            uiHandler.post(() -> {
                tvStatus.setText("WLS position fix from raw pseudoranges");
                tvLatLng.setText(String.format(Locale.US, "%.6f, %.6f", lat, lng));

                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "Altitude: %.1f m\n", alt));
                sb.append(String.format(Locale.US, "Satellites used: %d\n\n", measurements.size()));
                for (GnssMeasurement m : measurements) {
                    sb.append(String.format(Locale.US,
                            "SVID %3d | Const %d | CN0 %5.1f dB-Hz | PseudorangeRate %10.1f m/s\n",
                            m.getSvid(), m.getConstellationType(),
                            m.getCn0DbHz(), m.getPseudorangeRateMetersPerSecond()));
                }
                tvDetails.setText(sb.toString());
            });

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Log.e(TAG, "WLS error: " + msg, e);
            uiHandler.post(() -> {
                tvStatus.setText("WLS error: " + msg);
                showSatelliteDetails(measurements);
            });
        }
    }

    private void showSatelliteDetails(Collection<GnssMeasurement> measurements) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Satellites visible: %d\n\n", measurements.size()));
        for (GnssMeasurement m : measurements) {
            sb.append(String.format(Locale.US,
                    "SVID %3d | Const %d | CN0 %5.1f dB-Hz\n",
                    m.getSvid(), m.getConstellationType(), m.getCn0DbHz()));
        }
        tvDetails.setText(sb.toString());
    }
}
