/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.connectivity;

import android.content.Intent;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import javax.net.ssl.HttpsURLConnection;

import io.bytesatwork.skycell.Settings;
import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CloudConnection {
    private static final String TAG = CloudConnection.class.getSimpleName();

    private final SkyCellApplication app;
    private final ScheduledExecutorService mExecutor;
    private ScheduledFuture<?> mFuture;

    //Intent Action
    public static final String ACTION_SKYCELL_CLOUD_ONLINE =
        "io.bytesatwork.skycell.ACTION_SKYCELL_CLOUD_ONLINE";
    public static final String EXTRA_SKYCELL_CLOUD_ONLINE =
        "io.bytesatwork.skycell.EXTRA_SKYCELL_CLOUD_ONLINE";

    public CloudConnection() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        this.mExecutor = Executors.newScheduledThreadPool(1);
        this.mFuture = null;
    }

    /**
     * Check if the server with the given cloudUrl is reachable.
     *
     * @param cloudUrl The url of the cloud
     * @return Return true if successful, otherwise false.
     */
    public boolean isServerReachable(String cloudUrl)
    {
        boolean ok = false;
        HttpsURLConnection connection = null;

        try {
            URL url = new URL(cloudUrl);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            url = new URL(baseUrl);
            connection = (HttpsURLConnection) url.openConnection();
            Object content = connection.getContent();
            Log.v(TAG + ":" + Utils.getLineNumber(), "Got content: " + content);
            ok = true;
        } catch (Exception e) {
            ok = false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return ok;
    }

    public boolean start(String cloudUrl) {
        Log.i(TAG + ":" + Utils.getLineNumber(), "Start");
        try {
            mFuture = mExecutor.scheduleAtFixedRate(new CloudConnection.CloudCheckerTask(cloudUrl),
                0, 10, SECONDS);
        } catch (RejectedExecutionException rejectedExecutionException) {
            rejectedExecutionException.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean stop() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "Stop");
        if (mFuture != null) {
            return mFuture.cancel(true);
        }

        return true;
    }

    public void shutdown() {
        stop();
        Log.i(TAG + ":" + Utils.getLineNumber(), "Shutdown");
        mExecutor.shutdown();
    }

    public boolean isRunning() {
        return ((mFuture != null) && !mFuture.isDone());
    }

    private class CloudCheckerTask implements Runnable {
        String mUrl;

        public CloudCheckerTask(String url) {
            this.mUrl = url;
        }

        public void run() {
            final Intent intent = new Intent(ACTION_SKYCELL_CLOUD_ONLINE);
            intent.putExtra(EXTRA_SKYCELL_CLOUD_ONLINE, isServerReachable(mUrl));
            app.sendBroadcast(intent);
        }
    }

    /**
     * Do a get request with the given cloudUrl and returns a response json.
     *
     * @param cloudUrl The url of the cloud
     * @param apiKey The apiKey to authenticate
     * @return Return response String (json) if successful, otherwise null.
     */
    public String get(String cloudUrl, String apiKey) {
        boolean ok = false;
        StringBuilder stringBuilder = new StringBuilder();
        HttpURLConnection connection = null;

        try {
            //header
            Log.i(TAG + ":" + Utils.getLineNumber(), "Get: " + cloudUrl);
            URL url = new URL(cloudUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("APIKEY", apiKey);

            //get status
            int responseCode = connection.getResponseCode();
            Log.i(TAG + ":" + Utils.getLineNumber(), "Status: " + responseCode);
            ok = (HttpURLConnection.HTTP_OK <= responseCode &&
                responseCode <= HttpURLConnection.HTTP_RESET);
            BufferedReader streamReader;
            String line;
            if (ok) {
                //read response
                streamReader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(connection.getInputStream())));

                while ((line = streamReader.readLine()) != null) {
                    stringBuilder.append(line);
                    Log.i(TAG + ":" + Utils.getLineNumber(), line);
                }
            } else {
                //read error
                streamReader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(connection.getErrorStream())));

                while ((line = streamReader.readLine()) != null) {
                    Log.w(TAG + ":" + Utils.getLineNumber(), line);
                }
            }
            streamReader.close();
        } catch (Exception e) {
            e.printStackTrace();
            ok = false;
        } finally {
            //disconnect
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (ok) {
            return stringBuilder.toString();
        } else {
            return null;
        }
    }

    /**
     * Posts the json to the given cloudUrl and returns a response json.
     *
     * @param json JSON-String to post
     * @param cloudUrl The url of the cloud
     * @param apiKey The apiKey to authenticate
     * @return Return response String (json) if successful, otherwise null.
     */
    public String post(String json, String cloudUrl, String apiKey) {
        boolean ok = false;
        StringBuilder stringBuilder = new StringBuilder();
        HttpURLConnection connection = null;

        try {
            //header
            int contentLength = json.getBytes().length;
            Log.i(TAG + ":" + Utils.getLineNumber(), "Send JSON to: " + cloudUrl +
                ", Content-Length: " + contentLength);
            URL url = new URL(cloudUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("APIKEY", apiKey);

            //send json
            DataOutputStream out = new DataOutputStream(connection.getOutputStream());
            out.writeBytes(json);
            out.flush();
            out.close();

            //get status
            int responseCode = connection.getResponseCode();
            Log.i(TAG + ":" + Utils.getLineNumber(), "Status: " + responseCode);
            ok = (HttpURLConnection.HTTP_OK <= responseCode &&
                responseCode <= HttpURLConnection.HTTP_RESET);
            BufferedReader streamReader;
            String line;
            if (ok) {
                //read response
                streamReader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(connection.getInputStream())));

                while ((line = streamReader.readLine()) != null) {
                    stringBuilder.append(line);
                    Log.i(TAG + ":" + Utils.getLineNumber(), line);
                }
            } else {
                //read error
                streamReader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(connection.getErrorStream())));

                while ((line = streamReader.readLine()) != null) {
                    Log.w(TAG + ":" + Utils.getLineNumber(), line);
                }
            }
            streamReader.close();
        } catch (Exception e) {
            e.printStackTrace();
            ok = false;
        } finally {
            //disconnect
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (ok) {
            return stringBuilder.toString();
        } else {
            return null;
        }
    }
}
