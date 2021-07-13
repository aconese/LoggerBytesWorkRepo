/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

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
