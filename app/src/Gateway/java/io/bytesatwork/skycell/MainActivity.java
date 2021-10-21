/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_LOCATION = 111;
    private static final int REQUEST_CODE_STORAGE = 123;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG + ":" + Utils.getLineNumber(), "onCreate");

        getWindow().setStatusBarColor(getColor(android.R.color.transparent));

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            Log.e(TAG + ":" + Utils.getLineNumber(), getString(R.string.location_not_supported));
            finish();
        } else if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG + ":" + Utils.getLineNumber(),
                getString(R.string.location_permission_denied));

            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_CODE_LOCATION);
        } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG + ":" + Utils.getLineNumber(), getString(R.string.storage_permission_denied));

            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_CODE_STORAGE);
        } else {
            startService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG + ":" + Utils.getLineNumber(), getString(R.string.storage_permission_denied));

                        ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_CODE_STORAGE);
                    } else {
                        startService();
                    }
                }  else {
                    finish();
                }
                break;
            case REQUEST_CODE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startService();
                }  else {
                    finish();
                }
                break;
        }
    }

    private void startService() {
        //Start Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //Android 8.0 SDK 26
            startForegroundService(new Intent(this, SkyCellService.class));
        } else {
            startService(new Intent(this, SkyCellService.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }

        finish();
    }
}
