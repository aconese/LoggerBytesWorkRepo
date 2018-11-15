package io.bytesatwork.skycell;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
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
