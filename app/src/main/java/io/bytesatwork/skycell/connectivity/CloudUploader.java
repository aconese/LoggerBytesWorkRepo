package io.bytesatwork.skycell.connectivity;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.bytesatwork.skycell.Constants;
import io.bytesatwork.skycell.Settings;
import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CloudUploader {
    private static final String TAG = CloudUploader.class.getSimpleName();

    private SkyCellApplication app;
    private ScheduledExecutorService mExecutor;

    public CloudUploader() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        //TODO: Use JobScheduler instead of ScheduledExecutorService
        this.mExecutor = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "Start");
        String path = app.getApplicationContext().getExternalFilesDir(null).getAbsolutePath();
        String url = app.mSettings.loadString(Settings.SHARED_PREFERENCES_SERVER_URL);
        long rate = app.mSettings.loadLong(Settings.SHARED_PREFERENCES_UPLOAD_RATE_SECS);
        mExecutor.scheduleAtFixedRate(new FileUploadTask(path, url), rate, rate, SECONDS);
    }

    public void stop() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "Stop");
        mExecutor.shutdown();
    }

    private class FileUploadTask implements Runnable {
        File mDirectory;
        String mUrl;

        public FileUploadTask(String path, String url) {
            this.mDirectory = new File(path);
            this.mUrl = url.endsWith("/") ?  url : url + "/";
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
                                if (upload(json.toString(), files[i].getName())) {
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

        private boolean upload(String json, String fileName) {
            boolean ok = false;
            HttpURLConnection connection = null;

            try {
                //header
                Log.i(TAG + ":" + Utils.getLineNumber(), "Send JSON to: " + mUrl + fileName);
                URL url = new URL(mUrl + fileName);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

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

                //read response
                BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(
                    new BufferedInputStream(connection.getInputStream())));
                String line = "";
                while ((line = responseStreamReader.readLine()) != null) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), line);
                }
                responseStreamReader.close();

                //disconnect
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return ok;
        }
    }
}
