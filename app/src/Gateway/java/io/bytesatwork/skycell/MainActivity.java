/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG+":"+Utils.getLineNumber(), "onCreate");

        getWindow().setStatusBarColor(getColor(android.R.color.transparent));

        //TODO: Request Location Permission here if not set

        //Start Service
        //if (!SkyCellService.isRunning()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //Android 8.0 SDK 26
                startForegroundService(new Intent(this, SkyCellService.class));
            } else {
                startService(new Intent(this, SkyCellService.class)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
            }
        //}

        finish();
    }
}
