package io.bytesatwork.skycell.sensor;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;

public class SensorDataHandler {
    private static final String TAG = SensorDataHandler.class.getSimpleName();
    private static final String CLOUD_IP_ADDRESS = "192.168.2.210";

    private Sensor mSensor;
    private SkyCellApplication app;
    private ExecutorService mExecutor;

    public SensorDataHandler(Sensor sensor) {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        this.mExecutor = Executors.newSingleThreadExecutor();
        this.mSensor = sensor;
    }

    public boolean upload() {
        String fileName = mSensor.getAddress() + "json";
        String json = mSensor.convertToJSONString();
        if (writeFile(json, fileName)) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "File write ok");
            //TODO: Get IP from settings
            mExecutor.execute(new FileUploadTask(json, fileName, CLOUD_IP_ADDRESS));
            return true;
        }

        return false;
    }

    private boolean writeFile(String json, String filename) {
        boolean ok = false;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(app.getApplicationContext().getExternalFilesDir(null)
                .getAbsolutePath(), filename);
            Log.i(TAG + ":" + Utils.getLineNumber(), "Write: File: " + file.getAbsolutePath());
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write(json.getBytes());

                //Close file
                fos.flush();
                fos.close();
                ok = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ok;
    }

    private class FileUploadTask implements Runnable {
        String mJson;
        String mFilename;
        String mIp;

        public FileUploadTask(String json, String filename, String ip) {
            this.mJson = json;
            mFilename = filename;
            this.mIp = ip;
        }

        public void run() {
            HttpURLConnection connection = null;
            try {
                //header
                Log.i(TAG + ":" + Utils.getLineNumber(), "Send JSON to: http://" + mIp +
                    "/skycell/" + mFilename);
                URL url = new URL("http://" + mIp + "/skycell/" + mFilename);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                //send json
                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                out.writeBytes(mJson);
                out.flush();
                out.close();

                //read response
                InputStream responseStream = new BufferedInputStream(connection.getInputStream());
                BufferedReader responseStreamReader = new BufferedReader(
                    new InputStreamReader(responseStream));
                String line = "";
                while ((line = responseStreamReader.readLine()) != null) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), line);
                }
                responseStreamReader.close();

                //Get Status
                Log.i(TAG + ":" + Utils.getLineNumber(), "Status: " +
                    connection.getResponseCode()); //HttpURLConnection.HTTP_OK

                //disconnect
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
