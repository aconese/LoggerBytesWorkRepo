/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.connectivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;

public class GPS {
    private static final String TAG = GPS.class.getSimpleName();

    private static final long MIN_LOCATION_UPDATE_TIME = 1000L;

    private SkyCellApplication app;
    private LocationManager mLocationManager;
    private String mProvider;
    private Location mLocation;

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            mLocation = loc;
            Log.d(TAG+":"+ Utils.getLineNumber(),
                "Location changed: " + loc.getLongitude() + " " + loc.getLatitude());

        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG+":"+ Utils.getLineNumber(),"provider disabled");
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG+":"+ Utils.getLineNumber(),"provider enabled");
        }

        @Override @Deprecated
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    public GPS() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        mLocationManager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        this.mProvider = mLocationManager.getBestProvider(criteria, true);

        //Init location
        Location loc = null;
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            loc = mLocationManager.getLastKnownLocation(mProvider);
        }
        if (loc == null) {
            this.mLocation = new Location(mProvider);
        } else {
            this.mLocation = loc;
        }
    }

    public void registerListener() {
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(mProvider, MIN_LOCATION_UPDATE_TIME, 0, mLocationListener);
            Log.i(TAG + ":" + Utils.getLineNumber(), "requestLocationUpdates for " + mProvider);
        } else {
            Log.w(TAG+":"+ Utils.getLineNumber(), "ACCESS_FINE_LOCATION permission denied!");
        }
    }

    public void removeListener() {
        mLocationManager.removeUpdates(mLocationListener);
    }

    public Location getPosition() {
        return mLocation;
    }
}
