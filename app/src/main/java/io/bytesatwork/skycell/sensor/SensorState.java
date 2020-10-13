package io.bytesatwork.skycell.sensor;

import io.bytesatwork.skycell.ByteBufferParser;
import io.bytesatwork.skycell.Utils;

import android.util.Log;

import java.io.Serializable;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SensorState implements Serializable {
    private static final String TAG = SensorState.class.getSimpleName();

    public static final int SENSOR_STATE_CONTAINERID_LENGTH = 23;
    public static final int SENSOR_STATE_DEVICEID_LENGTH = 23;
    public static final int SENSOR_STATE_SOFTWAREVERSION_LENGTH = 3;
    public static final int SENSOR_STATE_HARDWAREVERSION_LENGTH = 3;
    public static final int SENSOR_STATE_INTERVAL_LENGTH = 2;
    public static final int SENSOR_STATE_BATTERY_LENGTH = 1;
    public static final int SENSOR_STATE_NUMSENSORS_LENGTH = 1;
    public static final int SENSOR_STATE_RSSI_LENGTH = 1;

    private byte[] mContainerId;
    private byte[] mDeviceId;
    private byte[] mSoftwareVersion;
    private byte[] mHardwareVersion;
    private byte[] mInterval;
    private byte mBattery;
    private byte mNumSensors;
    private byte mRssi;
    public List<SensorInfo> mSensorInfos = new ArrayList<SensorInfo>();

    public SensorState() {
        this.mContainerId = new byte[SENSOR_STATE_CONTAINERID_LENGTH];
        this.mDeviceId = new byte[SENSOR_STATE_DEVICEID_LENGTH];
        this.mSoftwareVersion = new byte[SENSOR_STATE_SOFTWAREVERSION_LENGTH];
        this.mHardwareVersion = new byte[SENSOR_STATE_HARDWAREVERSION_LENGTH];
        this.mInterval = new byte[SENSOR_STATE_INTERVAL_LENGTH];
        this.mBattery = 0;
        this.mNumSensors = 0;
        this.mRssi = 0;
    }

    public static int getSensorStateLength() {
        return SENSOR_STATE_CONTAINERID_LENGTH +
            SENSOR_STATE_DEVICEID_LENGTH +
            SENSOR_STATE_SOFTWAREVERSION_LENGTH +
            SENSOR_STATE_HARDWAREVERSION_LENGTH +
            SENSOR_STATE_INTERVAL_LENGTH +
            SENSOR_STATE_BATTERY_LENGTH +
            SENSOR_STATE_NUMSENSORS_LENGTH +
            SENSOR_STATE_RSSI_LENGTH;
    }

    public int getSensorInfosLength() {
        return mNumSensors * SensorInfo.getSensorInfoLength();
    }

    public boolean parseState(byte[] stateBuffer) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "stateBuffer: " +
            Utils.convertBytesToHexString(stateBuffer) + " len: " + stateBuffer.length);
        ByteBufferParser parser = new ByteBufferParser(stateBuffer);
        mContainerId = parser.getNextBytes(SENSOR_STATE_CONTAINERID_LENGTH);
        mDeviceId = parser.getNextBytes(SENSOR_STATE_DEVICEID_LENGTH);
        mSoftwareVersion = parser.getNextBytes(SENSOR_STATE_SOFTWAREVERSION_LENGTH);
        mHardwareVersion = parser.getNextBytes(SENSOR_STATE_HARDWAREVERSION_LENGTH);
        mInterval = parser.getNextBytes(SENSOR_STATE_INTERVAL_LENGTH);
        mBattery = parser.getNextByte();
        mNumSensors = parser.getNextByte();
        mRssi = parser.getNextByte();

        return true;
    }

    public boolean parseSensorInfos(byte[] infoBuffer, ByteOrder order) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "infobuffer: " +
                Utils.convertBytesToHexString(infoBuffer) + " len: " + infoBuffer.length);
        ByteBufferParser parser = new ByteBufferParser(infoBuffer);

        for (int i = 0; i < mNumSensors; i++) {
            mSensorInfos.add(new SensorInfo(parser.getNextBytes(SensorInfo.getSensorInfoLength()), order));
        }
        return true;
    }

    public String getContainerId() {
        return Utils.convertBytesToString(mContainerId);
    }

    public String getDeviceId() {
        return Utils.convertBytesToString(mDeviceId);
    }

    public String getSoftwareVersion() {
        return new String("v" + Utils.convertByteToUnsigned(mSoftwareVersion[0]) + "." +
            Utils.convertByteToUnsigned(mSoftwareVersion[1]) + "." +
            Utils.convertByteToUnsigned(mSoftwareVersion[2]));
    }

    public String getHardwareVersion() {
        return new String("v" + Utils.convertByteToUnsigned(mHardwareVersion[0]) + "." +
            Utils.convertByteToUnsigned(mHardwareVersion[1]) + "." +
            Utils.convertByteToUnsigned(mHardwareVersion[2]));
    }

    public int getInterval() {
        return Utils.convertShortToUnsigned(Utils.convertBytesToShort(mInterval, 0,
            mInterval.length, ByteOrder.LITTLE_ENDIAN));
    }

    public int getBattery() {
        return Utils.convertByteToUnsigned(mBattery);
    }

    public String getBatteryString() {
        return getBattery() + "%";
    }

    public int getNumSensors() {
        return Utils.convertByteToUnsigned(mNumSensors);
    }

    public byte getRssi() {
        return mRssi;
    }
}
