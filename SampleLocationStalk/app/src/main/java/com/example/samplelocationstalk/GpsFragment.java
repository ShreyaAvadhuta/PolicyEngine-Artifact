package com.example.samplelocationstalk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tab 2 — Platform GPS provider.
 *
 * Key methods:
 *   LocationManager.requestLocationUpdates(GPS_PROVIDER, ...)
 *   onLocationChanged(Location)
 *   Location.getLatitude / getLongitude
 *   Location.getAltitude
 *   Location.getSpeed
 *   Location.getBearing
 *   Location.getAccuracy
 */
public class GpsFragment extends Fragment {

    private TextView tvStatus, tvLatLng, tvDetails;
    private LocationManager locationManager;
    private LocationListener gpsListener;

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

        tvStatus.setText("Waiting for GPS fix...");

        gpsListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location loc) {
                tvStatus.setText("GPS fix from " + loc.getProvider());
                tvLatLng.setText(String.format(Locale.US, "%.6f, %.6f",
                        loc.getLatitude(), loc.getLongitude()));

                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "Accuracy:  %.1f m\n", loc.getAccuracy()));

                if (loc.hasAltitude())
                    sb.append(String.format(Locale.US, "Altitude:  %.1f m\n", loc.getAltitude()));

                if (loc.hasSpeed())
                    sb.append(String.format(Locale.US, "Speed:     %.1f m/s (%.1f km/h)\n",
                            loc.getSpeed(), loc.getSpeed() * 3.6));

                if (loc.hasBearing())
                    sb.append(String.format(Locale.US, "Bearing:   %.1f deg\n", loc.getBearing()));

                sb.append(String.format(Locale.US, "Provider:  %s\n", loc.getProvider()));
                sb.append(String.format(Locale.US, "Time:      %s\n",
                        new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(loc.getTime()))));

                if (loc.getExtras() != null) {
                    int sats = loc.getExtras().getInt("satellites", -1);
                    if (sats >= 0)
                        sb.append(String.format(Locale.US, "Satellites: %d\n", sats));
                }

                sb.append(String.format(Locale.US, "Mock:      %s\n",
                        loc.isFromMockProvider() ? "YES" : "no"));

                tvDetails.setText(sb.toString());
            }

            @Override public void onProviderEnabled(@NonNull String provider) {
                tvStatus.setText("GPS enabled");
            }

            @Override public void onProviderDisabled(@NonNull String provider) {
                tvStatus.setText("GPS disabled");
            }
        };

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5_000L, 0f,
                gpsListener, Looper.getMainLooper());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationManager != null && gpsListener != null)
            locationManager.removeUpdates(gpsListener);
    }
}
