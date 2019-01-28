package io.bytesatwork.skycell;

import io.bytesatwork.skycell.sensor.SensorList;

import android.app.Application;
import android.content.Context;

public class SkyCellApplication extends Application {
    private static Context context;
    public SensorList mSensorList;
    public Settings mSettings;

    public void onCreate() {
        super.onCreate();
        SkyCellApplication.context = getApplicationContext();
        mSettings = new Settings();
    }

    public static Context getAppContext() {
        return SkyCellApplication.context;
    }
}
