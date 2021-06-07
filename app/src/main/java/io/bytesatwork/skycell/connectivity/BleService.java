/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public class BleService extends Service {
    private static final String TAG = BleService.class.getSimpleName();

    private final SkyCellApplication app;
    private ExecutorService mScheduler;
    private BlockingQueue<Bundle> mSendQueue;
    private Future<?> mSendTask;
    private boolean mRequestPending;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mGattServer;
    private BluetoothGattCharacteristic mGattCharCmd;

    //Intent Action
    public static final String ACTION_SKYCELL_CONNECTED =
        "io.bytesatwork.skycell.ACTION_SKYCELL_CONNECTED";
    public static final String ACTION_SKYCELL_CONNECTING =
        "io.bytesatwork.skycell.ACTION_SKYCELL_CONNECTING";
    public static final String ACTION_SKYCELL_DISCONNECTED =
        "io.bytesatwork.skycell.ACTION_SKYCELL_DISCONNECTED";
    public static final String ACTION_SKYCELL_CMD_ACK =
        "io.bytesatwork.skycell.ACTION_SKYCELL_CMD_ACK";
    public static final String ACTION_SKYCELL_STATE =
        "io.bytesatwork.skycell.ACTION_SKYCELL_STATE";
    public static final String ACTION_SKYCELL_DATA =
        "io.bytesatwork.skycell.ACTION_SKYCELL_DATA";
    public static final String ACTION_SKYCELL_DATA_ALL =
        "io.bytesatwork.skycell.ACTION_SKYCELL_DATA_ALL";
    public static final String ACTION_SKYCELL_EXTREMA =
        "io.bytesatwork.skycell.ACTION_SKYCELL_EXTREMA";
    public static final String ACTION_SKYCELL_EXTREMA_ALL =
        "io.bytesatwork.skycell.ACTION_SKYCELL_EXTREMA_ALL";
    public static final String ACTION_SKYCELL_EVENT =
        "io.bytesatwork.skycell.ACTION_SKYCELL_EVENT";
    public static final String ACTION_SKYCELL_EVENT_ALL =
        "io.bytesatwork.skycell.ACTION_SKYCELL_EVENT_ALL";

    //Intent Extra
    public static final String DEVICE_ADDRESS_SKYCELL =
        "io.bytesatwork.skycell.DEVICE_ADDRESS_SKYCELL";
    public static final String CMD_SKYCELL =
        "io.bytesatwork.skycell.CMD_SKYCELL";

    private final IBinder mBinder;

    /**
     * Callback for BLE data transfer
     */
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothDevice device, final int status, final int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(TAG + ":" + Utils.getLineNumber(), "onConnectionStateChange status: " + status + ", newState: " + newState);
            Sensor sensor = app.mSensorList.getSensorByAddress(device.getAddress());

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (sensor == null) {
                        sensor = new Sensor(BleService.this, device.getAddress());
                        app.mSensorList.addSensor(sensor);

                        broadcastUpdate(ACTION_SKYCELL_CONNECTING, device.getAddress());
                        Log.i(TAG + ":" + Utils.getLineNumber(), "Connected to GATT server.");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (sensor != null) {
                        sensor.close();
                        app.mSensorList.removeSensor(sensor);

                        Log.i(TAG + ":" + Utils.getLineNumber(), "Disconnected from GATT server.");
                        mRequestPending = false; //clear request pending
                        broadcastUpdate(ACTION_SKYCELL_DISCONNECTED, device.getAddress());
                    }
                }
            } else { //Connection timeout
                Log.e(TAG + ":" + Utils.getLineNumber(), "GATT error (133)!");
                if (sensor != null) {
                    sensor.close();
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
            Log.i(TAG + ":" + Utils.getLineNumber(), "onCharacteristicReadRequest characteristic: " + characteristic.getUuid() + ", offset: " + offset);

            if (mGattServer != null) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
            } else {
                Log.e(TAG + ":" + Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onDescriptorReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(TAG + ":" + Utils.getLineNumber(), "onDescriptorReadRequest requestId: " + requestId + ", offset: " + offset + ", descriptor: " + descriptor.getUuid());

            if (mGattServer != null) {
                if (Constants.SKYCELL_DESC_CCC_UUID.equals(descriptor.getUuid())) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, descriptor.getValue());
                } else {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
                }
            } else {
                Log.e(TAG + ":" + Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG + ":" + Utils.getLineNumber(), "onCharacteristicWriteRequest " +
                "characteristic: " + characteristic.getUuid() + ", value: " +
                Utils.convertBytesToReadableHexString(value) + ", responseNeeded: " +
                responseNeeded + ", preparedWrite: " + preparedWrite + ", requestId: " +
                requestId + ", offset: " + offset + ", value length: " + value.length);
            Sensor sensor = app.mSensorList.getSensorByAddress(device.getAddress());

            if (mGattServer != null) {
                if (Constants.SKYCELL_CHAR_CMD_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Got CMD Ack");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    handleCMDState(sensor, offset, value);
                } else if (Constants.SKYCELL_CHAR_STATE_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Got State");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    handleState(sensor, offset, value);
                } else if (Constants.SKYCELL_CHAR_DATA_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Got Data");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    handleData(sensor, offset, value);
                } else if (Constants.SKYCELL_CHAR_EXTREMA_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Got Extrema");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    handleExtrema(sensor, offset, value);
                } else if (Constants.SKYCELL_CHAR_EVENT_UUID.equals(characteristic.getUuid())) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Got Event");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    }

                    handleEvent(sensor, offset, value);
                } else {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Unknown Char");
                    if (responseNeeded) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
                    }
                }
            } else {
                Log.e(TAG + ":" + Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onDescriptorWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattDescriptor descriptor, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG + ":" + Utils.getLineNumber(), "onDescriptorWriteRequest descriptor: " + descriptor.getUuid() + ", value: " + Arrays.toString(value) + ", responseNeeded: " + responseNeeded + ", preparedWrite: " + preparedWrite);

            descriptor.setValue(value);

            if (mGattServer != null) {
                if (responseNeeded) {
                    if (Constants.SKYCELL_DESC_CCC_UUID.equals(descriptor.getUuid())) {
                        //send empty response
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);
                    } else {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, Constants.EMPTY_BYTES);
                    }

                    Log.i(TAG + ":" + Utils.getLineNumber(), "CCC enabled (=CONNECTED)");
                    broadcastUpdate(ACTION_SKYCELL_CONNECTED, device.getAddress());
                }
            } else {
                Log.e(TAG + ":" + Utils.getLineNumber(), "mGattServer is null");
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(TAG + ":" + Utils.getLineNumber(), "onExecuteWrite");
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, Constants.EMPTY_BYTES);

            if (execute) {
                //TODO: add buffer to data array list
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.i(TAG + ":" + Utils.getLineNumber(), "onNotificationSent status: " + status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onNotificationSent failed");
            }
        }

        @Override
        public void onServiceAdded(final int status, final BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG + ":" + Utils.getLineNumber(), "onServiceAdded status: " + status + ", service: " + service.getUuid());

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServiceAdded Adding Service failed..");
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.i(TAG + ":" + Utils.getLineNumber(), "onMtuChanged mtu: " + mtu);
        }
    };

    private final AdvertiseCallback mAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "Advertising onStartSuccess");
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "Advertising onStartFailure: " + errorCode);
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    Log.e(TAG + ":" + Utils.getLineNumber(), " ADVERTISE_FAILED_ALREADY_STARTED ");
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "ADVERTISE_FAILED_DATA_TOO_LARGE");
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "ADVERTISE_FAILED_INTERNAL_ERROR");
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                    break;
                default:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "ADVERTISE_FAILED");
                    break;
            }
            super.onStartFailure(errorCode);
        }
    };

    public BleService() {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
        this.mBinder = new LocalBinder();
    }

    private synchronized void broadcastUpdate(final String action, final String address) {
        final Intent intent = new Intent(action);
        intent.putExtra(DEVICE_ADDRESS_SKYCELL, address);
        sendBroadcast(intent);
    }

    private synchronized void handleCMDState(Sensor sensor, final int offset, final byte[] value) {
        if (sensor != null) {
            if (value.length == 2) {
                Log.d(TAG + ":" + Utils.getLineNumber(), "handleCMDState: 0x" +
                    Utils.convertBytesToHexString(value, offset, value.length,
                    ByteOrder.LITTLE_ENDIAN, Short.class));
                short ack = Utils.convertBytesToShort(value, 0, value.length,
                    ByteOrder.LITTLE_ENDIAN);
                if (ack == Constants.ACK_CMD_OK) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Ack: ACK_CMD_OK");
                } else if (ack == Constants.ACK_CMD_UNKNOWN) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Ack: ACK_CMD_UNKNOWN");
                } else if (ack == Constants.ACK_CMD_PARAM_UNKNOWN) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Ack: ACK_CMD_PARAM_UNKNOWN");
                } else if (ack == Constants.ACK_CMD_PENDING) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Ack: ACK_CMD_PENDING");
                } else if (ack == Constants.ACK_UNKNOWN) {
                    Log.w(TAG + ":" + Utils.getLineNumber(), "Ack: ACK_UNKNOWN");
                } else { //Read-Position
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Ack: Read Position: " + ack);
                    sensor.setReadPosition(ack);
                }
            }

            broadcastUpdate(ACTION_SKYCELL_CMD_ACK, sensor.getAddress());
        }
    }

    private synchronized void handleState(Sensor sensor, final int offset, final byte[] value) {
        if (sensor != null && !sensor.isStateCompleted()) {
            try {
                int length = Math.min(value.length, sensor.mStateBuffer.remaining());
                Log.d(TAG + ":" + Utils.getLineNumber(), "offset: " + offset + " length: " + length);
                sensor.mStateBuffer.put(Arrays.copyOfRange(value, 0, length));
                if (sensor.mStateBuffer.remaining() == 0) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "State package complete");

                    if (sensor.parseState()) {
                        Log.d(TAG + ":" + Utils.getLineNumber(), "New DeviceId: " +
                            sensor.getDeviceId() + " Num Sensors: " +
                            sensor.mState.getNumSensors() + " SW: " +
                            sensor.mState.getSoftwareVersion() + " HW: " +
                            sensor.mState.getHardwareVersion() + " Interval: " +
                            sensor.mState.getInterval() + " Battery: " +
                            sensor.mState.getBatteryString() + " RSSI: " +
                            sensor.mState.getRssi()
                        );
                        sensor.completeState();
                        if (value.length > length) {
                            // handle the rest of the data
                            handleState(sensor, 0, Arrays.copyOfRange(value, length, value.length));
                        }
                    } else {
                        Log.e(TAG + ":" + Utils.getLineNumber(), "Could not parse State");
                    }
                    sensor.mStateBuffer.clear();
                } else {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for " +
                        sensor.mStateBuffer.remaining() + " bytes remaining");
                }
            } catch (BufferOverflowException e) {
                Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received for State");
            }
        } else if (sensor != null && !sensor.areInfosCompleted()) {
            try {
                int len = Math.min(value.length, sensor.mSensorInfoBuffer.remaining());
                Log.d(TAG + ":" + Utils.getLineNumber(), "SensorInfo: " + offset + " len: " + len);
                sensor.mSensorInfoBuffer.put(Arrays.copyOfRange(value, 0, len));
                if (sensor.mSensorInfoBuffer.remaining() == 0) {
                    if (sensor.parseInfos()) {
                        Log.d(TAG + ":" + Utils.getLineNumber(), "SensorInfos: complete");

                        sensor.completeInfos();
                        broadcastUpdate(ACTION_SKYCELL_STATE, sensor.getAddress());
                    } else {
                        Log.e(TAG + ":" + Utils.getLineNumber(), "Could not parse Infos");
                    }
                    sensor.mSensorInfoBuffer.clear();
                } else {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for " +
                            sensor.mSensorInfoBuffer.remaining() + " bytes remaining");
                }
            } catch (BufferOverflowException e) {
                Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received for State");
            }
        }
    }

    private synchronized void handleData(Sensor sensor, final int offset, final byte[] value) {
        if (sensor != null && !sensor.isDataCompleted() && sensor.mDataBuffer != null) {
            if (value.length == 0) {
                Log.d(TAG + ":" + Utils.getLineNumber(), "All Data packages received");
                sensor.completeData();
                broadcastUpdate(ACTION_SKYCELL_DATA_ALL, sensor.getAddress());
            } else {
                try {
                    int parsed = 0;
                    while (parsed < value.length) {
                        int parseLength = Math.min(value.length, sensor.mDataBuffer.remaining());
                        Log.d(TAG + ":" + Utils.getLineNumber(), "offset: " +
                            offset + " parsed: " + parsed + " parseLength: " + parseLength);
                        sensor.mDataBuffer.put(Arrays.copyOfRange(value, parsed,
                            parsed + parseLength));
                        if (sensor.mDataBuffer.remaining() == 0) {
                            Log.d(TAG + ":" + Utils.getLineNumber(), "Data package complete");

                            if (sensor.parseData()) {
                                Log.d(TAG + ":" + Utils.getLineNumber(),
                                    "Wait for more Data packages");
                                broadcastUpdate(ACTION_SKYCELL_DATA, sensor.getAddress());
                            }
                            sensor.mDataBuffer.clear();
                        } else {
                            Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for " +
                                sensor.mDataBuffer.remaining() + " bytes remaining");
                        }

                        parsed = parsed + parseLength;
                    }
                } catch (BufferOverflowException e) {
                    Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received " +
                        "in the Data package");
                }
            }
        }
    }

    private synchronized void handleExtrema(Sensor sensor, final int offset, final byte[] value) {
        if (sensor != null && !sensor.isExtremaCompleted() && sensor.mExtremaBuffer != null) {
            if (value.length == 0) {
                Log.d(TAG + ":" + Utils.getLineNumber(), "All Extrema packages received");
                sensor.completeExtrema();
                broadcastUpdate(ACTION_SKYCELL_EXTREMA_ALL, sensor.getAddress());
            } else {
                try {
                    int parsed = 0;
                    while (parsed < value.length) {
                        int parse_len = Math.min(value.length, sensor.mExtremaBuffer.remaining());
                        Log.d(TAG + ":" + Utils.getLineNumber(), "offset: " +
                            offset + " parsed: " + parsed + " parse_len: " + parse_len);
                        sensor.mExtremaBuffer.put(Arrays.copyOfRange(value, parsed,
                            parsed + parse_len));
                        if (sensor.mExtremaBuffer.remaining() == 0) {
                            Log.d(TAG + ":" + Utils.getLineNumber(), "Extrema package complete");

                            if (sensor.parseExtrema()) {
                                Log.d(TAG + ":" + Utils.getLineNumber(),
                                    "Wait for more Extrema packages");
                                broadcastUpdate(ACTION_SKYCELL_EXTREMA, sensor.getAddress());
                            }
                            sensor.mExtremaBuffer.clear();
                        } else {
                            Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for " +
                                sensor.mExtremaBuffer.remaining() + " bytes remaining");
                        }

                        parsed = parsed + parse_len;
                    }
                } catch (BufferOverflowException e) {
                    Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received " +
                        "in the Extrema package");
                }
            }
        }
    }

    private synchronized void handleEvent(Sensor sensor, final int offset, final byte[] value) {
        if (sensor != null && !sensor.isEventCompleted() && sensor.mEventBuffer != null) {
            if (value.length == 0) {
                Log.d(TAG + ":" + Utils.getLineNumber(), "All Event packages received");
                sensor.completeEvent();
                broadcastUpdate(ACTION_SKYCELL_EVENT_ALL, sensor.getAddress());
            } else {
                try {
                    int parsed = 0;
                    while (parsed < value.length) {
                        int parse_len = Math.min(value.length, sensor.mEventBuffer.remaining());
                        Log.d(TAG + ":" + Utils.getLineNumber(), "offset: " +
                            offset + " parsed: " + parsed + " parse_len: " + parse_len);
                        sensor.mEventBuffer.put(Arrays.copyOfRange(value, parsed,
                            parsed + parse_len));
                        if (sensor.mEventBuffer.remaining() == 0) {
                            Log.d(TAG + ":" + Utils.getLineNumber(), "Event package complete");

                            if (sensor.parseEvent()) {
                                Log.d(TAG + ":" + Utils.getLineNumber(),
                                    "Wait for more Event packages");
                                broadcastUpdate(ACTION_SKYCELL_EVENT, sensor.getAddress());
                            }
                            sensor.mEventBuffer.clear();
                        } else {
                            Log.d(TAG + ":" + Utils.getLineNumber(), "Waiting for " +
                                sensor.mEventBuffer.remaining() + " bytes remaining");
                        }

                        parsed = parsed + parse_len;
                    }
                } catch (BufferOverflowException e) {
                    Log.e(TAG + ":" + Utils.getLineNumber(), "Too much data received " +
                        "in the Event package");
                }
            }
        }
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }


    @Override
    public void onCreate() {
        Log.d(TAG + ":" + Utils.getLineNumber(), "Ble Service created");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG + ":" + Utils.getLineNumber(), "Ble Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG + ":" + Utils.getLineNumber(), "onBind");

        //Init Queue
        mSendQueue = new LinkedBlockingQueue<>();

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
                Log.e(TAG + ":" + Utils.getLineNumber(), "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "Bluetooth is not enabled.");
            return false;
        }

        mGattServer = mBluetoothManager.openGattServer(app.getApplicationContext(), mGattServerCallback);
        if (mGattServer == null) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "gattServer is null, check Bluetooth is ON.");
            return false;
        }

        if (!mGattServer.addService(setupSkyCellService())) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "Adding Service failed");
            return false;
        }

        Log.i(TAG + ":" + Utils.getLineNumber(), "BLE Initialization is successful.");
        return true;
    }

    public boolean deinitialize() {
        if (mGattServer == null) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "mGattServer is null");
            return false;
        }

        Log.i(TAG + ":" + Utils.getLineNumber(), "mGattServer clearServices and close");
        mGattServer.clearServices();
        mGattServer.close();

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
        final BluetoothGattService SERVICE = new BluetoothGattService(Constants.SKYCELL_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Battery Level
        mGattCharCmd = new BluetoothGattCharacteristic(
            Constants.SKYCELL_CHAR_CMD_UUID,
            BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);

        final BluetoothGattDescriptor DESC_CCC = new BluetoothGattDescriptor(
            Constants.SKYCELL_DESC_CCC_UUID,
            BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        DESC_CCC.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        mGattCharCmd.addDescriptor(DESC_CCC);

        final BluetoothGattCharacteristic CHAR_STATE = new BluetoothGattCharacteristic(
            Constants.SKYCELL_CHAR_STATE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);

        final BluetoothGattCharacteristic CHAR_DATA = new BluetoothGattCharacteristic(
            Constants.SKYCELL_CHAR_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);

        final BluetoothGattCharacteristic CHAR_EXTREMA = new BluetoothGattCharacteristic(
            Constants.SKYCELL_CHAR_EXTREMA_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);

        final BluetoothGattCharacteristic CHAR_EVENT = new BluetoothGattCharacteristic(
            Constants.SKYCELL_CHAR_EVENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);

        SERVICE.addCharacteristic(mGattCharCmd);
        SERVICE.addCharacteristic(CHAR_STATE);
        SERVICE.addCharacteristic(CHAR_DATA);
        SERVICE.addCharacteristic(CHAR_EXTREMA);
        SERVICE.addCharacteristic(CHAR_EVENT);

        return SERVICE;
    }

    public synchronized void scan(final boolean enable, String uuid) {
        Log.i(TAG + ":" + Utils.getLineNumber(), "Scan enable: " + enable);
        BluetoothLeScanner mScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        if (mScanner != null) {
            if (enable) {
                ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build();
                try {
                    mScanner.startScan(scanFilters(uuid), settings, mScanCallback);
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Scanning started");
                } catch (IllegalArgumentException e) {
                    Log.e(TAG + ":" + Utils.getLineNumber(), e.getMessage());
                }
            } else {
                mScanner.stopScan(mScanCallback);
                Log.i(TAG + ":" + Utils.getLineNumber(), "Scanning stopped");
            }
        } else {
            Log.e(TAG + ":" + Utils.getLineNumber(), "mScanner is null");
        }
    }

    private List<ScanFilter> scanFilters(String uuid) {
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString(uuid))).build();
        List<ScanFilter> list = new ArrayList<>(1);
        list.add(filter);
        return list;
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                Log.d(TAG, "onScanResult: " + device.getName() + " " + device.getAddress() + " " + result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "SCAN_FAILED_ALREADY_STARTED");
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED");
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "SCAN_FAILED_FEATURE_UNSUPPORTED");
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "SCAN_FAILED_INTERNAL_ERROR");
                    break;
                default:
                    Log.e(TAG + ":" + Utils.getLineNumber(), "SCAN_FAILED");
                    break;
            }


            super.onScanFailed(errorCode);
        }
    };

    //TODO: implement
    public synchronized boolean isAdvertising() {
        return true;
    }

    public synchronized boolean advertise(final boolean enable) {
        return this.advertise(enable, null);
    }

    public synchronized boolean advertise(final boolean enable, String containerId) {
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        if (enable) {
            ByteBuffer serviceData;
            byte isGateway = (byte) (Utils.isGateway() ? 1 : 0);
            AdvertiseSettings settings;

            //Connection to specific container with containerId desired
            if (containerId != null && containerId.length() > 0) {
                byte[] cid = Utils.convertStringToBytes(containerId);
                serviceData = ByteBuffer.allocate(cid.length + 1);
                serviceData.order(ByteOrder.LITTLE_ENDIAN);
                serviceData.put(isGateway);
                serviceData.put(cid);

                settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            } else {
                serviceData = ByteBuffer.allocate(1);
                serviceData.order(ByteOrder.LITTLE_ENDIAN);
                serviceData.put(isGateway);

                settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            }

            int deviceNameLength = BluetoothAdapter.getDefaultAdapter().getName().length();
            AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(deviceNameLength > 14 ? false : true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(Constants.SKYCELL_SERVICE_PARCELUUID)
                .addServiceData(Constants.SKYCELL_SERVICE_PARCELUUID, serviceData.array())
                .build();
            if (deviceNameLength > 14) { //Add deviceName to scan response if it's longer than 14
                AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeDeviceName(deviceNameLength > 31 ? false : true)
                    .build();
                advertiser.startAdvertising(settings, data, scanResponse, mAdvertisingCallback);
            } else {
                advertiser.startAdvertising(settings, data, mAdvertisingCallback);
            }
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

    public synchronized boolean sendConfig(String addr, long timeStamp, short interval,
                                           short transmissionRateMultiple) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(11);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_CONFIG);
            cmd.putLong(timeStamp);
            cmd.putShort(interval);
            cmd.putShort(transmissionRateMultiple);

            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendStartTrip(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_START_TRIP);

            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendEndTrip(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_END_TRIP);

            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendGetState(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_GET_STATE);

            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendReadData(String addr, long timeStamp) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(9);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_READ_DATA);
            cmd.putLong(timeStamp);

            sensor.clearData();
            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendReadCurrentData(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_READ_CURRENT_DATA);

            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendReadExtrema(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_READ_EXTREMA);

            sensor.clearExtrema();
            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendReadEvent(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_READ_EVENT);

            sensor.clearEvent();
            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendClear(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(3);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_CLEAR);
            cmd.putShort(sensor.getReadPosition());

            sensor.clearState();
            sensor.clearInfos();
            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendDisconnect(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_DISCONNECT);

            return sendCMD(addr, cmd.array());
        }
        return false;
    }

    public synchronized boolean sendDecommission(String addr) {
        Sensor sensor = app.mSensorList.getSensorByAddress(addr);
        if (sensor != null) {
            ByteBuffer cmd = ByteBuffer.allocate(1);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            cmd.put(Constants.CMD_DECOMMISSION);

            return sendCMD(addr, cmd.array());
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
            sensor.close();
            app.mSensorList.removeSensor(sensor);
            broadcastUpdate(ACTION_SKYCELL_DISCONNECTED, addr);
        }
        return false;
    }

    private synchronized boolean sendCMD(BluetoothDevice device, byte[] cmd) {
        mGattCharCmd.setValue(cmd);
        Log.d(TAG+":"+Utils.getLineNumber(), "notifyCharacteristicChanged cmd: 0x" +
            Utils.convertBytesToHexString(cmd, 0, cmd.length, ByteOrder.LITTLE_ENDIAN,
            Byte.class));
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
                sensor.close();
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
        private final BlockingQueue<Bundle> queue;

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
            while ((app.mSensorList.getSensorByAddress(b.getString(DEVICE_ADDRESS_SKYCELL)) != null) && mRequestPending) {
                Thread.sleep(10);
            }
        }
    }
}