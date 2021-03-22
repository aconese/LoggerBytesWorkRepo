/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.connectivity;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import io.bytesatwork.skycell.Utils;

public class CloudConnection {
    private static final String TAG = CloudConnection.class.getSimpleName();

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
            String line = "";
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
