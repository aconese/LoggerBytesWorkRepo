/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.connectivity;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import io.bytesatwork.skycell.Constants;
import io.bytesatwork.skycell.Settings;
import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CloudUploader {
    private static final String TAG = CloudUploader.class.getSimpleName();

    private SkyCellApplication app;
    private ScheduledExecutorService mExecutor;
    private CloudConnection mConnection;
    private ScheduledFuture<?> mFuture;

    public CloudUploader() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        //TODO: Use WorkManager instead of ScheduledExecutorService
        this.mExecutor = Executors.newScheduledThreadPool(1);
        this.mConnection = new CloudConnection();
        this.mFuture = null;
    }

    public void start() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "Start");
        String path = app.getApplicationContext().getExternalFilesDir(null).getAbsolutePath();
        String url = app.mSettings.loadString(Settings.SHARED_PREFERENCES_URL_UPLOAD);
        String apiKey = app.mSettings.loadString(Settings.SHARED_PREFERENCES_APIKEY);
        long rate = app.mSettings.loadLong(Settings.SHARED_PREFERENCES_UPLOAD_RATE_SECS);
        mFuture = mExecutor.scheduleAtFixedRate(new FileUploadTask(path, url, apiKey), rate, rate,
            SECONDS);
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

    private class FileUploadTask implements Runnable {
        File mDirectory;
        String mUrl;
        String mApikey;

        public FileUploadTask(String path, String url, String apikey) {
            this.mDirectory = new File(path);
            this.mUrl = url.endsWith("/") ?  url : url + "/";
            this.mApikey = apikey;
        }

        public void run() {
            Log.i(TAG + ":" + Utils.getLineNumber(), "run");
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                if (mDirectory.isDirectory()) {
                    File[] files = mDirectory.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(Constants.FILE_ENDING);
                        }
                    });
                    for (int i = 0; i < files.length; ++i) {
                        Log.i(TAG + ":" + Utils.getLineNumber(), "Read: (" +
                            (i + 1) + " / " + files.length + ") " + files[i].getName());
                        try {
                            BufferedReader buffreader = new BufferedReader(new InputStreamReader(
                                new FileInputStream(files[i])));
                            StringBuffer json = new StringBuffer("");
                            String line = "";
                            while ((line = buffreader.readLine()) != null) {
                                json.append(line);
                            }
                            buffreader.close();
                            if (json.length() > 0) {
                                if (mConnection.post(json.toString(), mUrl, mApikey) != null) {
                                    Log.i(TAG + ":" + Utils.getLineNumber(),
                                        "Upload ok - delete file");
                                    files[i].delete();
                                } else {
                                    Log.w(TAG + ":" + Utils.getLineNumber(), "Upload failed");
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
