package io.bytesatwork.skycell;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class SkyCellService extends Service {
    private static final String TAG = SkyCellService.class.getSimpleName();
    public static final String CHANNEL_ID = "io.bytesatwork.skycell.CHANNEL_ID";
    public static final String CHANNEL_NAME = "Channel";

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public SkyCellService getService() {
            return SkyCellService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG+":"+Utils.getLineNumber(), "onCreate");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG+":"+Utils.getLineNumber(), "onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG+":"+Utils.getLineNumber(), "onBind");

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG+":"+Utils.getLineNumber(), "onStartCommand");

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.gw_service_running))
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //Android 8.0 SDK 26
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);

            //Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            notificationManager.notify(startId, notificationBuilder.build());
        } else {
            startForeground(startId, notificationBuilder.build());
        }

        //We want this service to continue running until it is explicitly stopped
        return START_STICKY;
    }
}
