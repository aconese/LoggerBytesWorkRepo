/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.connectivity.CloudConnection;
import io.bytesatwork.skycell.connectivity.CloudUploader;
import io.bytesatwork.skycell.connectivity.KeepAliveJobService;
import io.bytesatwork.skycell.sensor.Sensor;
import io.bytesatwork.skycell.sensor.SensorList;

public class SkyCellService extends Service {
    private static final String TAG = SkyCellService.class.getSimpleName();
    public static final String CHANNEL_ID = "io.bytesatwork.skycell.CHANNEL_ID";
    public static final String CHANNEL_NAME = "Channel";

    private SkyCellApplication app;
    private static SkyCellService instance = null;
    private final IBinder mBinder;

    private BluetoothAdapter mBluetoothAdapter;
    private static BleService mBleService = null;
    private boolean mBleServiceBound = false;
    private ExecutorService mExecutor;
    private long startTime = 0;
    private CloudUploader mCloudUploader;
    private KeepAliveJobService mKeepAlive;
    private CloudConnection mConnection;
    private String mUploadURL;
    private final ConnectivityManager mConnectivityManager;

    public SkyCellService() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        this.mBinder = new LocalBinder();
        this.mExecutor = Executors.newSingleThreadExecutor();
        this.mCloudUploader = new CloudUploader();
        this.mKeepAlive = new KeepAliveJobService();
        this.mConnection = new CloudConnection();
        this.mUploadURL = "";
        this.mConnectivityManager = (ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static boolean isRunning() {
        Log.d(TAG, "running: " + (instance != null));
        return instance != null;
    }
    
    public class LocalBinder extends Binder {
        public SkyCellService getService() {
            return SkyCellService.this;
        }
    }

    //Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_ON:
                        //Indicates the local Bluetooth adapter is turning on. However local clients should wait for STATE_ON before attempting to use the adapter.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_TURNING_ON");
                        break;

                    case BluetoothAdapter.STATE_ON:
                        //Indicates the local Bluetooth adapter is on, and ready for use.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_ON");
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        //Indicates the local Bluetooth adapter is turning off. Local clients should immediately attempt graceful disconnection of any remote links.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_TURNING_OFF");
                        break;

                    case BluetoothAdapter.STATE_OFF:
                        //Indicates the local Bluetooth adapter is off.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_OFF");
                        break;

                    default:
                        break;
                }
            } else {
                final Sensor sensor = app.mSensorList.getSensorByAddress(
                    intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));

                if (BleService.ACTION_SKYCELL_CONNECTED.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_CONNECTED");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalConnected();
                    }
                } else if (BleService.ACTION_SKYCELL_CONNECTING.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_CONNECTING");
                } else if (BleService.ACTION_SKYCELL_DISCONNECTED.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_DISCONNECTED");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalDisconnected();
                    }
                } else if (BleService.ACTION_SKYCELL_CMD_ACK.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_CMD_ACK");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalAck();
                    }
                } else if (BleService.ACTION_SKYCELL_STATE.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_STATE");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalStateComplete();
                    }
                } else if (BleService.ACTION_SKYCELL_DATA.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_DATA");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalData();
                    }
                } else if (BleService.ACTION_SKYCELL_DATA_ALL.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_DATA_ALL");
                    Log.i(TAG+":"+Utils.getLineNumber(), "time readData: " +
                        (System.currentTimeMillis() - startTime) / 1000 + "sec");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalDataComplete();
                    }
                } else if (BleService.ACTION_SKYCELL_EXTREMA.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_EXTREMA");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalExtrema();
                    }
                } else if (BleService.ACTION_SKYCELL_EXTREMA_ALL.equals(action)) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "ACTION_SKYCELL_EXTREMA_ALL");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalExtremaComplete();
                    }
                } else if (BleService.ACTION_SKYCELL_EVENT.equals(action)) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "ACTION_SKYCELL_EVENT");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalEvent();
                    }
                } else if (BleService.ACTION_SKYCELL_EVENT_ALL.equals(action)) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "ACTION_SKYCELL_EVENT_ALL");
                    if (sensor != null) {
                        sensor.mSensorSessionFSM.signalEventComplete();
                        startTime = System.currentTimeMillis();
                    }
                }
            }
        }
    };

    private static IntentFilter setupBroadcastReceiverFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_SKYCELL_CONNECTED);
        intentFilter.addAction(BleService.ACTION_SKYCELL_CONNECTING);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_SKYCELL_CMD_ACK);
        intentFilter.addAction(BleService.ACTION_SKYCELL_STATE);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DATA);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DATA_ALL);
        intentFilter.addAction(BleService.ACTION_SKYCELL_EXTREMA);
        intentFilter.addAction(BleService.ACTION_SKYCELL_EXTREMA_ALL);
        intentFilter.addAction(BleService.ACTION_SKYCELL_EVENT);
        intentFilter.addAction(BleService.ACTION_SKYCELL_EVENT_ALL);
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

    //NetworkCallback starts/stops cloud services on network changes
    private final ConnectivityManager.NetworkCallback mNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "The default network is now: " + network);
            NetworkCapabilities networkCapabilities =
                mConnectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                if (mConnection.isServerReachable(mUploadURL)) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Network connected");
                    if (!mCloudUploader.isRunning()) {
                        mCloudUploader.start();
                    }
                    if (!mKeepAlive.isRunning()) {
                        mKeepAlive.start();
                    }
                }
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "The application no longer has a default" +
                " network. The last default network was " + network);
            if (mCloudUploader.isRunning()) {
                mCloudUploader.stop();
            }
            if (mKeepAlive.isRunning()) {
                mKeepAlive.stop();
            }
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            Log.d(TAG + ":" + Utils.getLineNumber(), "The default network changed " +
                "capabilities: " + networkCapabilities);
        }

        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
            Log.d(TAG + ":" + Utils.getLineNumber(), "The default network changed link " +
                "properties: " + linkProperties);
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
                //Create new SensorList
                app.mSensorList = new SensorList();
                //Create or load SharedPreferences
                mUploadURL = app.mSettings.loadString(Settings.SHARED_PREFERENCES_URL_UPLOAD);
                String apikey = app.mSettings.loadString(Settings.SHARED_PREFERENCES_APIKEY);
                //Create UUID if missing on init
                String uuid = app.mSettings.loadString(Settings.SHARED_PREFERENCES_UUID);
                long rate = app.mSettings.loadLong(Settings.SHARED_PREFERENCES_UPLOAD_RATE_SECS);
                Log.i(TAG+":"+Utils.getLineNumber(), "Initializing SkyCellService" +
                    " (Upload URL: " + mUploadURL + ", Rate: " + rate + "secs)");
            }

            if (!checkStoragePermission() || !initializeLocation() || !initializeBluetooth()
                || !initializeNetworkCallback()) {
                stopSelf();
                return;
            }

            if (mBleService == null) {
                registerReceiver(mGattUpdateReceiver, setupBroadcastReceiverFilter());
                bindService(new Intent(instance, BleService.class), mServiceConnection,
                    BIND_AUTO_CREATE);
            }
        }

        public boolean checkStoragePermission() {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG + ":" + Utils.getLineNumber(), getString(R.string.storage_permission_denied));
                return false;
            }

            return true;
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

        public boolean initializeNetworkCallback() {
            if (mConnectivityManager != null) {
                mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
                return true;
            }

            return false;
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
            unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            mBleServiceBound = false;
        }
        mBleService = null;
        mExecutor.shutdown();
        mCloudUploader.shutdown();
        mKeepAlive.stop();

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
