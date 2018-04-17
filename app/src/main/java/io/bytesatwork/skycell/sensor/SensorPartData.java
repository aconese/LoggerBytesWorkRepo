package io.bytesatwork.skycell.sensor;

import io.bytesatwork.skycell.Utils;

import android.util.Log;

import java.nio.ByteOrder;
import java.util.Arrays;

public class SensorPartData {
    private static final String TAG = SensorPartData.class.getSimpleName();

    private byte mHasMore;
    private byte[] mTimeDiff;
    private byte[] mInsideTemp;
    private byte[] mOutsideTemp;
    private byte[] mSignature;

    private SensorPartData() {
        this.mHasMore = 0;
        this.mTimeDiff = new byte[4];
        this.mInsideTemp = new byte[2];
        this.mOutsideTemp = new byte[2];
        this.mSignature = null;
    }

    public SensorPartData(byte[] partDataBuffer) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "partDataBuffer len: "+partDataBuffer.length);
        this.mHasMore = partDataBuffer[0];
        this.mTimeDiff = Arrays.copyOfRange(partDataBuffer, 1, 5);
        this.mInsideTemp = Arrays.copyOfRange(partDataBuffer, 5, 7);
        this.mOutsideTemp = Arrays.copyOfRange(partDataBuffer, 7, 9);
        this.mSignature = null;
    }

    public boolean hasMore() {
        return (this.mHasMore == (byte)0x01);
    }

    public int getTimeDiff() {
        return Utils.convertBytesToInt(this.mTimeDiff, 0, this.mTimeDiff.length, ByteOrder.LITTLE_ENDIAN);
    }

    public double getInsideTemp() {
        return ((double) Utils.convertBytesToInt(this.mInsideTemp, 0, this.mInsideTemp.length, ByteOrder.LITTLE_ENDIAN) / 10);
    }

    public String getInsideTempString() {
        return String.format("%.1f", getInsideTemp());
    }

    public double getOutsideTemp() {
        return ((double) Utils.convertBytesToInt(this.mOutsideTemp, 0, this.mOutsideTemp.length, ByteOrder.LITTLE_ENDIAN) / 10);
    }

    public String getOutsideTempString() {
        return String.format("%.1f", getOutsideTemp());
    }

    public String getSignature() {
        return "";
    }
}
