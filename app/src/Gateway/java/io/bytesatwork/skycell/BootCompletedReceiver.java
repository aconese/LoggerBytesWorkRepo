package io.bytesatwork.skycell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG+":"+Utils.getLineNumber(), "onReceive");

        //Start Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //Android 8.0 SDK 26
            context.startForegroundService(new Intent(context, SkyCellService.class));
        } else {
            context.startService(new Intent(context, SkyCellService.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }
    }
}
