package io.bytesatwork.skycell.sensor;

import android.support.annotation.IntDef;
import android.support.annotation.StringDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteOrder;

import io.bytesatwork.skycell.ByteBufferParser;
import io.bytesatwork.skycell.Utils;

public class SensorExtrema {
    private static final String TAG = SensorExtrema.class.getSimpleName();

    //ExtremaType Enum
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ExtremaType.MIN, ExtremaType.MAX})
    public @interface ExtremaType {
        byte MIN = 0;
        byte MAX = 1;
    }
    @StringDef({ExtremaTypeString.MIN, ExtremaTypeString.MAX})
    public @interface ExtremaTypeString {
        String MIN = "min";
        String MAX = "max";
    }

    public static final int SENSOR_EXTREMA_PERIOD_START_LENGTH = 8;
    public static final int SENSOR_EXTREMA_PERIOD_END_LENGTH = 8;
    public static final int SENSOR_EXTREMA_TIMESTAMP_LENGTH = 8;
    public static final int SENSOR_EXTREMA_SENSORID_LENGTH = 8;
    public static final int SENSOR_EXTREMA_TYPE_LENGTH = 1;
    public static final int SENSOR_EXTREMA_VALUE_LENGTH = 2;

    public static final int SENSOR_EXTREMA_BINARYDATA_LENGTH = (
        SENSOR_EXTREMA_PERIOD_START_LENGTH +
        SENSOR_EXTREMA_PERIOD_END_LENGTH +
        SENSOR_EXTREMA_TIMESTAMP_LENGTH +
        SENSOR_EXTREMA_SENSORID_LENGTH +
        SENSOR_EXTREMA_TYPE_LENGTH +
        SENSOR_EXTREMA_VALUE_LENGTH
    );
    public static final int SENSOR_EXTREMA_SIGNATURE_LENGTH = 64;

    private byte[] mBinaryData;
    private byte[] mSignature;

    private byte[] mPeriodStart;
    private byte[] mPeriodEnd;
    private byte[] mTimeStamp;
    private long mUID;
    private byte mType;
    private byte[] mValue;

    private SensorExtrema() {
        this.mBinaryData = new byte[SENSOR_EXTREMA_BINARYDATA_LENGTH];
        this.mSignature = new byte[SENSOR_EXTREMA_SIGNATURE_LENGTH];

        this.mPeriodStart = new byte[SENSOR_EXTREMA_PERIOD_START_LENGTH];
        this.mPeriodEnd = new byte[SENSOR_EXTREMA_PERIOD_END_LENGTH];
        this.mTimeStamp = new byte[SENSOR_EXTREMA_TIMESTAMP_LENGTH];
        this.mUID = 0;
        this.mType = 0;
        this.mValue = new byte[SENSOR_EXTREMA_VALUE_LENGTH];
    }

    public SensorExtrema(byte[] extremaBuffer) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "extremaBuffer len: "+extremaBuffer.length);
        ByteBufferParser parser = new ByteBufferParser(extremaBuffer);
        this.mBinaryData = parser.getNextBytes(SENSOR_EXTREMA_BINARYDATA_LENGTH);
        this.mSignature = parser.getNextBytes(SENSOR_EXTREMA_SIGNATURE_LENGTH);

        parser = new ByteBufferParser(mBinaryData);
        this.mPeriodStart = parser.getNextBytes(SENSOR_EXTREMA_PERIOD_START_LENGTH);
        this.mPeriodEnd = parser.getNextBytes(SENSOR_EXTREMA_PERIOD_END_LENGTH);
        this.mTimeStamp = parser.getNextBytes(SENSOR_EXTREMA_TIMESTAMP_LENGTH);
        this.mUID = Utils.convertBytesToLong(parser.getNextBytes(SENSOR_EXTREMA_SENSORID_LENGTH),
            0, SENSOR_EXTREMA_SENSORID_LENGTH, ByteOrder.LITTLE_ENDIAN);
        this.mType = parser.getNextByte();
        this.mValue = parser.getNextBytes(SENSOR_EXTREMA_VALUE_LENGTH);
    }

    public static int getSensorExtremaLength() {
        return SENSOR_EXTREMA_BINARYDATA_LENGTH +
            SENSOR_EXTREMA_SIGNATURE_LENGTH;
    }

    public String getBinaryData() {
        return Utils.convertBytesToHexString(mBinaryData);
    }

    public String getSignature() {
        return Utils.convertBytesToHexString(mSignature);
    }

    public @ExtremaTypeString String getType() {
        if (mType == ExtremaType.MIN) {
            return ExtremaTypeString.MIN;
        } else {
            return ExtremaTypeString.MAX;
        }
    }

    public String getUTCPeriodStartTimeStamp() {
        long startTimeStamp = Utils.convertBytesToTimeStamp(mPeriodStart, 0,
            mPeriodStart.length, ByteOrder.LITTLE_ENDIAN);
        return Utils.convertTimeStampToUTCString(startTimeStamp * 1000);
    }

    public String getUTCPeriodEndTimeStamp() {
        long endTimeStamp = Utils.convertBytesToTimeStamp(mPeriodEnd, 0,
            mPeriodEnd.length, ByteOrder.LITTLE_ENDIAN);
        return Utils.convertTimeStampToUTCString(endTimeStamp * 1000);
    }

    public String getUTCTimeStamp() {
        long timeStamp = Utils.convertBytesToTimeStamp(mTimeStamp, 0, mTimeStamp.length,
            ByteOrder.LITTLE_ENDIAN);
        return Utils.convertTimeStampToUTCString(timeStamp * 1000);
    }

    public String getUUID() { return Long.toHexString(mUID); }

    public float getValue() {
        int v = Utils.convertBytesToShort(mValue, 0, mValue.length, ByteOrder.LITTLE_ENDIAN);
        return (float) v / 10;
    }
}
