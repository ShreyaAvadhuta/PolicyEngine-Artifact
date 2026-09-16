package com.example.samplelocationstalk;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LocationPagerAdapter extends FragmentStateAdapter {

    private static final String[] TITLES = {"GNSS", "GPS", "Wi-Fi", "Cellular"};

    public LocationPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new GnssFragment();
            case 1: return new GpsFragment();
            case 2: return new WifiFragment();
            case 3: return new CellularFragment();
            default: throw new IllegalArgumentException("Invalid tab: " + position);
        }
    }

    public static String getTitle(int position) {
        return TITLES[position];
    }
}
