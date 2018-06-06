package io.bytesatwork.skycell.connectivity;

import io.bytesatwork.skycell.Constants;
import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.Utils;
import io.bytesatwork.skycell.sensor.Sensor;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.nio.BufferOverflowException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public class BleService extends Service {
    private static final String TAG = BleService.class.getSimpleName();

    public SkyCellApplication app = ((SkyCellApplication) SkyCellApplication.getAppContext());
    private ExecutorService mScheduler;
    private BlockingQueue<Bundle> mSendQueue;
    private Future<?> mSendTask;
    private boolean mRequestPending;
    private final Handler mHandler = new Handler(app.getApplicationContext().getMainLooper());

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mGattServer;
    private BluetoothGattCharacteristic mGattCharCmd;

    //Intent Action
    public static final String ACTION_SKYCELL_CONNECTED = "io.bytesatwork.skycell.ACTION_SKYCELL_CONNECTED";
    public static final String ACTION_SKYCELL_DISCONNECTED = "io.bytesatwork.skycell.ACTION_SKYCELL_DISCONNECTED";
    public static final String ACTION_SKYCELL_CMD_ACK = "io.bytesatwork.skycell.ACTION_SKYCELL_CMD_ACK";
    public static final String ACTION_SKYCELL_DATA = "io.bytesatwork.skycell.ACTION_SKYCELL_DATA";
    public static final String ACTION_SKYCELL_DATA_ALL = "io.bytesatwork.skycell.ACTION_SKYCELL_DATA_ALL";
    public static final String ACTION_SKYCELL_STATE = "io.bytesatwork.skycell.ACTION_SKYCELL_STATE";

    //Intent Extra
    public static final String DEVICE_ADDRESS_SKYCELL = "io.bytesatwork.skycell.DEVICE_ADDRESS_SKYCELL";
    public static final String CMD_SKYCELL = "io.bytesatwork.skycell.CMD_SKYCELL";

    private final IBinder mBinder = new LocalBinder();

    /**
     * Callback for BLE data transfer
     */
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothDevice device, final int status, final int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(TAG+":"+Utils.getLineNumber(), "onConnectionStateChange status: " + status + ", newState: " + newState);
            Sensor sensor = app.mSensorList.getSensorByAddress(device.getAddress());

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (sensor == null) {
                        sensor = new Sensor(device.getAddress());
                        app.mSensorList.addSensor(sensor);

                        broadcastUpdate(ACTION_SKYCELL_CONNECTED, device.getAddress());
                        Log.i(TAG + ":" + Utils.getLineNumber(), "Connected to GATT server.");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (sensor != null) {
                        app.mSensorList.removeSensor(sensor);

                        Log.i(TAG + ":" + Utils.getLineNumber(), "Disconnected from GATT server.");
                        mRequestPending = false; //clear request pending
                        broadcastUpdate(ACTION_SKYCELL_DISCONNECTED, device.getAddress());
                    }
                }
            } else { //Connection timeout
                Log.e(TAG + ":" + Utils.getLineNumber(), "GATT error (133)!");
                if (sensor != null) {
                    app.mSensorList.removeSensor(sensor);

                    Log.i(TAG + ":" + Utils.getLineNumber(), "Disconnected from GATT server.");
                    mRequestPending = false; //clear request pending
                    broadcastUpdate(ACTION_SKYCELL_DISCONNECTED, device.getAddress());
                }
            }
        }

        @Override
        public void onCharacteristicReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(TAG+":"+Utils.getLineNumber(), "onCharacteristicReadRequest characteristic: " + characteristic.getUuid() + ", offset: " + offset);

            if (mGattServer != null) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
            } else {
                Log.e(TAG+":"+Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onDescriptorReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(TAG+":"+Utils.getLineNumber(), "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.getUuid());

            if (mGattServer != null) {
                if (Constants.SKYCELL_DESC_CCC_UUID.equals(descriptor.getUuid())) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, descriptor.getValue());
                } else {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
                }
            } else {
                Log.e(TAG+":"+Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG+":"+Utils.getLineNumber(), "onCharacteristicWriteRequest characteristic: "+characteristic.getUuid()+", value: "+Arrays.toString(value)+", responseNeeded: "+responseNeeded+", preparedWrite: "+preparedWrite+", requestId: "+requestId+", offset: "+offset+", value length: "+value.length);
            Sensor sensor = app.mSensorList.getSensorByAddress(device.getAddress());

            if (mGattServer != null) {
                if (Constants.SKYCELL_CHAR_CMD_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "Got CMD Ack");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    if (sensor != null) {
                        broadcastUpdate(ACTION_SKYCELL_CMD_ACK, sensor.getAddress());
                    }
                } else if (Constants.SKYCELL_CHAR_DATA_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "Got Data");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    if (sensor != null) {
                        try {
                            int parsed = 0;
                            while (parsed < value.length) {
                                int parse_len = value.length <= sensor.mPartDataBuffer.remaining() ? value.length : sensor.mPartDataBuffer.remaining();
                                Log.d(TAG + ":" + Utils.getLineNumber(), "offset: " + offset + " parsed: " + parsed + " parse_len: " + parse_len);
                                sensor.mPartDataBuffer.put(Arrays.copyOfRange(value, parsed, parsed+parse_len));
                                if (sensor.mPartDataBuffer.remaining() == 0) {
                                    Log.d(TAG + ":" + Utils.getLineNumber(), "Part Data package complete");

                                    if (sensor.parsePartData()) {
                                        if (sensor.mData.get(sensor.mData.size() - 1).hasMore()) {
                                            Log.d(TAG + ":" + Utils.getLineNumber(), "Wait for more Part Data packages");
                                            broadcastUpdate(ACTION_SKYCELL_DATA, sensor.getAddress());
                                        } else {
                                            Log.d(TAG + ":" + Utils.getLineNumber(), "All Part Data packages received");
                                            broadcastUpdate(ACTION_SKYCELL_DATA_ALL, sensor.getAddress());
                                            parsed = value.length;
                                        }
                                    }
                                    sensor.mPartDataBuffer.clear();
                                } else {
                                    Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for " + sensor.mPartDataBuffer.remaining() + " bytes remaining");
                                }

                                parsed = parsed + parse_len;
                            }
                        } catch (BufferOverflowException e) {
                            Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received for Part Data");
                        }
                    }
                } else if (Constants.SKYCELL_CHAR_STATE_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "Got State");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    if (sensor != null) {
                        try {
                            int len = value.length <= sensor.mStateBuffer.remaining() ? value.length : sensor.mStateBuffer.remaining();
                            Log.d(TAG + ":" + Utils.getLineNumber(), "offset: "+offset+" len: "+len);
                            sensor.mStateBuffer.put(Arrays.copyOfRange(value, 0, len));
                            if (sensor.mStateBuffer.remaining() == 0) {
                                Log.d(TAG + ":" + Utils.getLineNumber(), "State package complete");

                                if (sensor.parseState()) {
                                    Log.d(TAG + ":" + Utils.getLineNumber(), "New id: "+sensor.getIdString());
                                    broadcastUpdate(ACTION_SKYCELL_STATE, sensor.getAddress());
                                } else {
                                    Log.e(TAG + ":" + Utils.getLineNumber(), "Could not parse State");
                                }
                                sensor.mStateBuffer.clear();
                            } else {
                                Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for "+sensor.mStateBuffer.remaining()+" bytes remaining");
                            }
                        } catch (BufferOverflowException e) {
                            Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received for State");
                        }
                    }
                } else {
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
                    }
                }
            } else {
                Log.e(TAG+":"+Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onDescriptorWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattDescriptor descriptor, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG+":"+Utils.getLineNumber(), "onDescriptorWriteRequest descriptor: " + descriptor.getUuid() + ", value: " + Arrays.toString(value) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite);

            descriptor.setValue(value);

            if (mGattServer != null) {
                if (responseNeeded) {
                    if (Constants.SKYCELL_DESC_CCC_UUID.equals(descriptor.getUuid())) {
                        //send empty response
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    } else {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
                    }

                    //send getState cmd
                    sendGetState(device.getAddress());
                }
            } else {
                Log.e(TAG+":"+Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(TAG+":"+Utils.getLineNumber(), "onExecuteWrite");
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);

            if (execute) {
                //TODO: add part buffer to data array list
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.i(TAG+":"+Utils.getLineNumber(), "onNotificationSent status: " + status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onNotificationSent failed");
            }
        }

        @Override
        public void onServiceAdded(final int status, final BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG+":"+Utils.getLineNumber(), "onServiceAdded status: " + status + ", service: " + service.getUuid());

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServiceAdded Adding Service failed..");
            }
        }
    };

    private final AdvertiseCallback mAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG+":"+Utils.getLineNumber(), "Advertising onStartSuccess");
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG+":"+Utils.getLineNumber(), "Advertising onStartFailure: " + errorCode);
            super.onStartFailure(errorCode);
        }
    };

    private synchronized void broadcastUpdate(final String action, final String address) {
        final Intent intent = new Intent(action);
        intent.putExtra(DEVICE_ADDRESS_SKYCELL, address);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }


    @Override
    public void onCreate() {
        Log.d(TAG+":"+Utils.getLineNumber(), "Ble Service created");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG+":"+Utils.getLineNumber(), "Ble Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG+":"+Utils.getLineNumber(), "onBind");

        //Init Queue
        mSendQueue = new LinkedBlockingQueue<Bundle>();

        //Start Executor
        mScheduler = Executors.newCachedThreadPool();

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //Stop Executor
        if (mScheduler != null) mScheduler.shutdown();

        return super.onUnbind(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG+":"+Utils.getLineNumber(), "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG+":"+Utils.getLineNumber(), "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG+":"+Utils.getLineNumber(), "Bluetooth is not enabled.");
            return false;
        }

        mGattServer = mBluetoothManager.openGattServer(app.getApplicationContext(), mGattServerCallback);
        if (mGattServer == null) {
            Log.e(TAG+":"+Utils.getLineNumber(),"gattServer is null, check Bluetooth is ON.");
            return false;
        }
        addService(setupSkyCellService());

        BluetoothAdapter.getDefaultAdapter().setName(Constants.SKYCELL_DEVICE_NAME);

        Log.i(TAG+":"+Utils.getLineNumber(), "BLE Initialization is successful.");

        return true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    private synchronized void close(BluetoothGatt gatt) {
        if (gatt == null) {
            return;
        }
        gatt.close();
        gatt = null;
    }

    private synchronized BluetoothGattService setupSkyCellService() {
        final BluetoothGattService service = new BluetoothGattService(Constants.SKYCELL_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Battery Level
        mGattCharCmd = new BluetoothGattCharacteristic(
                Constants.SKYCELL_CHAR_CMD_UUID,
                BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        final BluetoothGattDescriptor desc_ccc = new BluetoothGattDescriptor(
                Constants.SKYCELL_DESC_CCC_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        desc_ccc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        mGattCharCmd.addDescriptor(desc_ccc);

        final BluetoothGattCharacteristic char_data = new BluetoothGattCharacteristic(
                Constants.SKYCELL_CHAR_DATA_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        final BluetoothGattCharacteristic char_state = new BluetoothGattCharacteristic(
                Constants.SKYCELL_CHAR_STATE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);



        while (!service.addCharacteristic(mGattCharCmd));
        while (!service.addCharacteristic(char_data));
        while (!service.addCharacteristic(char_state));

        return service;
    }

    /**
     * Add GATT service to gattServer
     *
     * @param service the service
     */
    private synchronized void addService(final BluetoothGattService service) {
        boolean serviceAdded = false;
        while (!serviceAdded) {
            try {
                serviceAdded = mGattServer.addService(service);
            } catch (final Exception e) {
                Log.d(TAG+":"+Utils.getLineNumber(), "Adding Service failed", e);
            }
        }
        Log.i(TAG+":"+Utils.getLineNumber(), "Service: " + service.getUuid() + " added.");
    }

    public synchronized boolean advertise(final boolean enable) {
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        if (enable) {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(true)
                    .addServiceUuid(Constants.SKYCELL_SERVICE_PARCELUUID)
                    //.addManufacturerData()
                    //.addServiceData()
                    .build();
            advertiser.startAdvertising(settings, data, mAdvertisingCallback);
        } else {
            advertiser.stopAdvertising(mAdvertisingCallback);
        }

        return true;
    }

    public synchronized String getAddress() {
        return BluetoothAdapter.getDefaultAdapter().getAddress();
    }

    public synchronized String getName() {
        return BluetoothAdapter.getDefaultAdapter().getName();
    }

    public synchronized boolean isConnected(String addr) {
        List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        for(BluetoothDevice device : devices) {
            if (device.getAddress().equals(addr)) {
                return true;
            }
        }

        return false;
    }

    public synchronized void emptySendQueue() {
        mSendQueue.clear();
    }

    public synchronized boolean sendConfig(String addr) {
        byte[] cmd = {Constants.CMD_CONFIG};
        //TODO: implement
        return false;
    }

    public synchronized boolean sendRead(String addr) {
        byte[] cmd = {Constants.CMD_READ};
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);

        if (sensor != null) {
            sensor.clearData();
            return sendCMD(addr, cmd);
        }
        return false;
    }

    public synchronized boolean sendClear(String addr) {
        byte[] cmd = {Constants.CMD_CLEAR};
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);

        if (sensor != null) {
            sensor.clearData();
            return sendCMD(addr, cmd);
        }
        return false;
    }

    public synchronized boolean sendGetState(String addr) {
        byte[] cmd = {Constants.CMD_GET_STATE};
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);

        if (sensor != null) {
            sensor.clearState();
            return sendCMD(addr, cmd);
        }
        return false;
    }

    public synchronized boolean sendDisconnect(String addr) {
        byte[] cmd = {Constants.CMD_DISCONNECT};
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);

        if (sensor != null) {
            return sendCMD(addr, cmd);
        }
        return false;
    }

    private synchronized boolean sendCMD(String addr, byte[] cmd) {
        List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        for(BluetoothDevice device : devices) {
            if (device.getAddress().equals(addr)) {
                return sendCMD(device, cmd);
            }
        }

        Log.w(TAG+":"+Utils.getLineNumber(), "Sensor already disconnected!");
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        mRequestPending = false; //clear request pending
        if (sensor != null) {
            app.mSensorList.removeSensor(sensor);
            broadcastUpdate(ACTION_SKYCELL_DISCONNECTED, addr);
        }
        return false;
    }

    private synchronized boolean sendCMD(BluetoothDevice device, byte[] cmd) {
        mGattCharCmd.setValue(cmd);
        return mGattServer.notifyCharacteristicChanged(device, mGattCharCmd, true); //indication
    }

    private synchronized boolean sendCMD(BluetoothGatt gatt, final int[] cmd) {
        if (mBluetoothAdapter == null || gatt == null || cmd == null) return false;

        byte[] val = {(byte)0xC0};
        mGattCharCmd.setValue(val);
        mGattServer.notifyCharacteristicChanged(gatt.getDevice(), mGattCharCmd, true); //indication

        //Check if the device already disconnected while app was not running
        if (!mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT).contains(gatt.getDevice())) {
            Log.w(TAG+":"+Utils.getLineNumber(), "Device already disconnected!");
            Sensor sensor = app.mSensorList.getSensorByAddress(gatt.getDevice().getAddress());
            mRequestPending = false; //clear request pending
            if (sensor != null) {
                app.mSensorList.removeSensor(sensor);
                broadcastUpdate(ACTION_SKYCELL_DISCONNECTED, gatt.getDevice().getAddress());
            }
            close(gatt);
            return false;
        }

        //Send cmd
        Bundle b = new Bundle();
        b.putString(DEVICE_ADDRESS_SKYCELL, gatt.getDevice().getAddress());
        b.putIntArray(CMD_SKYCELL, cmd);
        try {
            if (mSendTask == null) {
                mSendTask = mScheduler.submit(new SendTask(mSendQueue));
            }

            mSendQueue.put(b);
            Log.d(TAG+":"+Utils.getLineNumber(), "Put to sendQueue");
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private class SendTask implements Runnable {
        private BlockingQueue<Bundle> queue;

        public SendTask(BlockingQueue<Bundle> queue) {
            this.queue = queue;
        }

        public void run() {
            //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
            //Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                while (true) {
                    consume(queue.take());
                    //Log.e(TAG+":"+Utils.getLineNumber(), "QueueSize: "+queue.size());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void consume(Bundle b) throws InterruptedException {
            //long startTime = System.currentTimeMillis();

            while ((app.mSensorList.getSensorByAddress(b.getString(DEVICE_ADDRESS_SKYCELL)) != null) && mRequestPending) {
                Thread.sleep(10);
            }
            if (app.mSensorList.getSensorByAddress(b.getString(DEVICE_ADDRESS_SKYCELL)) == null) return;
        }
    }
}