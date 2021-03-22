/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.connectivity;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import io.bytesatwork.skycell.Constants;
import io.bytesatwork.skycell.Settings;
import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;

public class KeepAliveJobService extends JobService {
    private static final String TAG = KeepAliveJobService.class.getSimpleName();

    private static final String RESPONSE_KEY_UTC_TIME = "utcTime";
    private static final int JOB_ID = 42;
    private static final long ONE_DAY_INTERVAL = 24 * 60 * 60 * 1000L; // 1 Day
    private static final int MAX_RETRIES = 3;

    private SkyCellApplication app;
    private CloudConnection mConnection;
    private GPS mGPS;

    public KeepAliveJobService() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        this.mConnection = new CloudConnection();
        this.mGPS = new GPS();
        mGPS.registerListener();
    }

    public void start() {
        JobScheduler jobScheduler = (JobScheduler)
            app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName componentName =
            new ComponentName(app, KeepAliveJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, componentName);
        builder.setPersisted(false); //Don't persist across device reboots
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setPeriodic(ONE_DAY_INTERVAL);
        jobScheduler.schedule(builder.build());

        sendKeepAlive(); //Call once on startup
    }

    public void stop() {
        JobScheduler jobScheduler = (JobScheduler)
            app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        //run keepAlive (main thread)
        for (int i = 0; i < MAX_RETRIES; ++i) {
            if (sendKeepAlive()) break;
        }

        //job completed
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        //reschedule the job
        return true;
    }

    private boolean sendKeepAlive() {
        boolean ok = false;

        try {
            JSONObject request = new JSONObject();
            request.put("gatewayUuid", app.mSettings.loadString(Settings.SHARED_PREFERENCES_UUID));
            request.put("gatewayConnected", Constants.GATEWAY_STATUS_ONLINE);
            request.put("longitude", mGPS.getPosition().getLongitude());
            request.put("latitude", mGPS.getPosition().getLatitude());

            String json = request.toString(2);
            Log.i(TAG + ":" + Utils.getLineNumber(), "Send KeepAlive: " + json);
            String url = app.mSettings.loadString(Settings.SHARED_PREFERENCES_URL_KEEPALIVE);
            String apiKey = app.mSettings.loadString(Settings.SHARED_PREFERENCES_APIKEY);
            String responseString = mConnection.post(json, url, apiKey);
            if (responseString != null) {
                ok = true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            ok = false;
        }

        return ok;
    }
}
