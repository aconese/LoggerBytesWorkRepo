package io.bytesatwork.skycell.sensor;

import io.bytesatwork.skycell.Utils;

import android.util.Log;

import java.io.Serializable;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

public class SensorState implements Serializable {
    private static final String TAG = SensorState.class.getSimpleName();

    private byte[] mId;
    private byte[] mInterval;
    private byte[] mVersion;
    private byte mState;
    private byte[] mUtcStart;
    private byte mBattery;
    private byte mRssi;
    private byte[] mMinInsideTemp;
    private byte[] mMaxInsideTemp;

    public SensorState() {
        this.mId = new byte[4];
        this.mInterval = new byte[2];
        this.mVersion = new byte[3];
        this.mState = 0;
        this.mUtcStart = new byte[8];
        this.mBattery = 0;
        this.mRssi = 0;
        this.mMinInsideTemp = new byte[2];
        this.mMaxInsideTemp = new byte[2];
    }

    public boolean parseState(byte[] stateBuffer) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "stateBuffer len: "+stateBuffer.length);
        this.mId = Arrays.copyOfRange(stateBuffer, 0, 4);
        Log.d(TAG+":"+ Utils.getLineNumber(), "mId: "+Utils.convertBytesToReadableHexString(this.mId));
        this.mInterval = Arrays.copyOfRange(stateBuffer, 4, 6);
        this.mVersion = Arrays.copyOfRange(stateBuffer, 6, 9);
        this.mState = stateBuffer[9];
        this.mUtcStart = Arrays.copyOfRange(stateBuffer, 10, 18);
        this.mBattery = stateBuffer[18];
        this.mRssi = stateBuffer[19];
        this.mMinInsideTemp = Arrays.copyOfRange(stateBuffer, 20, 22);
        this.mMaxInsideTemp = Arrays.copyOfRange(stateBuffer, 22, 24);
        return true;
    }

    public int getId() {
        return Utils.convertBytesToInt(this.mId, 0, this.mId.length, ByteOrder.LITTLE_ENDIAN);
    }

    public String getIdString() {
        return Long.toHexString(Utils.convertIntToUnsignedLong(getId())).toUpperCase();
    }

    public String getUtcStartString() {
        Calendar cal = Calendar.getInstance();
        long utcStart = Utils.convertBytesToLong(this.mUtcStart, 0, this.mUtcStart.length, ByteOrder.LITTLE_ENDIAN);
        cal.setTimeInMillis(utcStart*1000);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(cal.getTime());
    }

    public double getMinInsideTemp() {
        return ((double) Utils.convertBytesToInt(this.mMinInsideTemp, 0, this.mMinInsideTemp.length, ByteOrder.LITTLE_ENDIAN) / 10);
    }

    public String getMinInsideTempString() {
        return String.format("%.1f°", getMinInsideTemp());
    }

    public double getMaxInsideTemp() {
        return ((double) Utils.convertBytesToInt(this.mMaxInsideTemp, 0, this.mMaxInsideTemp.length, ByteOrder.LITTLE_ENDIAN) / 10);
    }

    public String getMaxInsideTempString() {
        return String.format("%.1f°", getMaxInsideTemp());
    }

    public int getRssi() {
        return this.mRssi;
    }

    public int getBattery() {
        return mBattery;
    }

    public String getBatteryString() {
        return Integer.toString(mBattery)+"%";
    }
}
