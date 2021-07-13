/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    public static final String SHARED_PREFERENCES_SERVER_URL =
        "io.bytesatwork.skycell.SHARED_PREFERENCES_SERVER_URL";
    public static final String SHARED_PREFERENCES_UPLOAD_RATE_SECS =
        "io.bytesatwork.skycell.SHARED_PREFERENCES_UPLOAD_RATE_SECS";
    public static final String SHARED_PREFERENCES_APIKEY =
         "io.bytesatwork.skycell.SHARED_PREFERENCES_APIKEY";
    private static final String SHARED_PREFERENCES = "io.bytesatwork.skycell.SHARED_PREFERENCES";
    private SharedPreferences mPreferences;
    private SkyCellApplication app;

    public Settings() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        mPreferences = app.getSharedPreferences(Settings.SHARED_PREFERENCES,
            Context.MODE_PRIVATE);
    }

    public String loadString(String key) {
        String setting = "";

        switch (key) {
            case SHARED_PREFERENCES_SERVER_URL:
                setting = app.getString(R.string.default_pref_server_url);
                break;
            case SHARED_PREFERENCES_APIKEY:
                setting = app.getString(R.string.default_pref_api_key);
            default:
                break;
        }

        return setting;
    }

    public long loadLong(String key) {
        long setting = 0;

        switch (key) {
            case SHARED_PREFERENCES_UPLOAD_RATE_SECS:
                setting = loadSettings(key, Constants.UPLOAD_RATE);
                break;
            default:
                break;
        }

        return setting;
    }

    public void saveString(String key, String value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public void saveLong(String key, long value) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putLong(key, value);
        editor.commit();
    }

    private String loadSettings(String key, String defaultValue) {
        if (!mPreferences.contains(key)) {
            saveString(key, defaultValue);
        }

        return mPreferences.getString(key, defaultValue);
    }

    private long loadSettings(String key, long defaultValue) {
        if (!mPreferences.contains(key)) {
            saveLong(key, defaultValue);
        }

        return mPreferences.getLong(key, defaultValue);
    }
}
