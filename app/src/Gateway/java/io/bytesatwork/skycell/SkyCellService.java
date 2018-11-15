package io.bytesatwork.skycell;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.sensor.SensorList;

public class SkyCellService extends Service {
    private static final String TAG = SkyCellService.class.getSimpleName();
    public static final String CHANNEL_ID = "io.bytesatwork.skycell.CHANNEL_ID";
    public static final String CHANNEL_NAME = "Channel";

    public SkyCellApplication app = ((SkyCellApplication) SkyCellApplication.getAppContext());
    private static SkyCellService instance = null;
    private final IBinder mBinder = new LocalBinder();

    private BluetoothAdapter mBluetoothAdapter;
    private static BleService mBleService = null;
    private boolean mBleServiceBound = false;
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public static boolean isRunning() {
        Log.d(TAG, "running: " + (instance != null));
        return instance != null;
    }

    public class LocalBinder extends Binder {
        public SkyCellService getService() {
            return SkyCellService.this;
        }
    }

    private static IntentFilter setupBroadcastReceiverFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_SKYCELL_CONNECTED);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_SKYCELL_STATE);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DATA);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DATA_ALL);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.setPriority(Constants.SKYCELL_INTENT_FILTER_HIGH_PRIORITY);
        return intentFilter;
    }

    //Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "onServiceConnected");
            mBleService = ((BleService.LocalBinder) service).getService();
            if (!mBleService.initialize()) {
                Log.e(TAG + ":" + Utils.getLineNumber(), "Unable to initialize Bluetooth");
                stopSelf();
            }
            mBleServiceBound = true;

            if (mBluetoothAdapter.isEnabled()) {
                mBleService.advertise(true); //Start permanent advertising
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "permission granted");
                    mBleService.scan(true, Constants.SKYCELL_SERVICE);
                } else {
                    Log.d(TAG, "no permission");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "onServiceDisconnected");
            mBleService.advertise(false); //Stop permanent advertising
            mBleService = null;
            mBleServiceBound = false;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "onBindingDied");
            stopSelf();
        }

        public void onNullBinding(ComponentName name) {
            Log.e(TAG+":"+Utils.getLineNumber(),"onNullBinding");
            stopSelf();
        }
    };

    private class GatewayTask implements Runnable {
        public GatewayTask() {
        }

        public void run() {
            initialize();
        }

        public void initialize() {
            if (app != null) {
                app.mSensorList = new SensorList();
            }

            if (!initializeLocation()) {
                stopSelf();
            } else if (!initializeBluetooth()) {
                stopSelf();
            }

            if (mBleService == null) {
                bindService(new Intent(instance, BleService.class), mServiceConnection,
                    BIND_AUTO_CREATE);
            }
        }

        public boolean initializeLocation() {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
                Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.location_not_supported));
                return false;
            } else if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.location_permission_denied));
                return false;
            }

            return true;
        }

        public boolean initializeBluetooth() {
            //Check if BLE is supported on the device.
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.ble_not_supported));
                return false;
            } else if (!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()) {
                //Check for advertisement support
                Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.ble_adv_not_supported));
                return false;
            }

            //Initializes a Bluetooth adapter.
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();

            if (mBluetoothAdapter == null) {
                return false;
            } else if (!mBluetoothAdapter.isEnabled()) {
                return mBluetoothAdapter.enable();
            }

            return true;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG+":"+Utils.getLineNumber(), "onCreate");
        instance = this;
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG+":"+Utils.getLineNumber(), "onDestroy");
        instance = null;

        if (mBleServiceBound && mBleService != null) {
            unbindService(mServiceConnection);
            mBleServiceBound = false;
        }
        mBleService = null;
        mExecutor.shutdown();

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
        Log.i(TAG+":"+Utils.getLineNumber(), "onStartCommand " + flags + " " + startId);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this,
            CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.gw_service_running))
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //Android 8.0 SDK 26
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);

            //Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        startForeground(startId, notificationBuilder.build());

        mExecutor.execute(new GatewayTask());

        //We want this service to continue running until it is explicitly stopped
        return START_STICKY;
    }
}
