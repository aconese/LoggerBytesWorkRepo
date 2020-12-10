package io.bytesatwork.skycell.sensor;

import android.support.annotation.IntDef;
import android.support.annotation.StringDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteOrder;

import io.bytesatwork.skycell.ByteBufferParser;
import io.bytesatwork.skycell.Utils;

public class SensorEvent {
    private static final String TAG = SensorEvent.class.getSimpleName();

    //SensorEventType Enum
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SensorEventType.DOOR})
    public @interface SensorEventType {
        byte DOOR = 0;
    }
    @StringDef({SensorEventTypeString.DOOR})
    public @interface SensorEventTypeString {
        String DOOR = "DOOR";
    }

    //SensorEventValue Enum
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SensorEventDoorValue.CLOSED, SensorEventDoorValue.OPEN})
    public @interface SensorEventDoorValue {
        int CLOSED = 0;
        int OPEN = 1;
    }
    @StringDef({SensorEventValueString.DOOR_CLOSED, SensorEventValueString.DOOR_OPEN,
        SensorEventValueString.UNKNOWN})
    public @interface SensorEventValueString {
        String DOOR_CLOSED = "CLOSED";
        String DOOR_OPEN = "OPEN";
        String UNKNOWN = "";
    }

    public static final int SENSOR_EVENT_TIMESTAMP_LENGTH = 8;
    public static final int SENSOR_EVENT_TYPE_LENGTH = 1;
    public static final int SENSOR_EVENT_SPECIFIC_LENGTH = 1;
    public static final int SENSOR_EVENT_VALUE_LENGTH = 2;

    public static final int SENSOR_EVENT_BINARYDATA_LENGTH = (
        SENSOR_EVENT_TIMESTAMP_LENGTH +
        SENSOR_EVENT_TYPE_LENGTH +
        SENSOR_EVENT_SPECIFIC_LENGTH +
        SENSOR_EVENT_VALUE_LENGTH
    );

    private byte[] mBinaryData;

    private byte mType;
    private byte[] mTimeStamp;
    private byte mSpecific;
    private byte[] mValue;

    private SensorEvent() {
        this.mBinaryData = new byte[SENSOR_EVENT_BINARYDATA_LENGTH];

        this.mTimeStamp = new byte[SENSOR_EVENT_TIMESTAMP_LENGTH];
        this.mType = 0;
        this.mSpecific = 0;
        this.mValue = new byte[SENSOR_EVENT_VALUE_LENGTH];
    }

    public SensorEvent(byte[] eventBuffer) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "eventBuffer len: "+eventBuffer.length);
        ByteBufferParser parser = new ByteBufferParser(eventBuffer);
        this.mBinaryData = parser.getNextBytes(SENSOR_EVENT_BINARYDATA_LENGTH);

        parser = new ByteBufferParser(mBinaryData);
        this.mTimeStamp = parser.getNextBytes(SENSOR_EVENT_TIMESTAMP_LENGTH);
        this.mType = parser.getNextByte();
        this.mSpecific = parser.getNextByte();
        this.mValue = parser.getNextBytes(SENSOR_EVENT_VALUE_LENGTH);
    }

    public static int getSensorEventLength() {
        return SENSOR_EVENT_BINARYDATA_LENGTH;
    }

    public String getBinaryData() {
        return Utils.convertBytesToHexString(mBinaryData);
    }

    public @SensorEventType int getType() {
        return mType;
    }

    public String getUTCTimeStamp() {
        long timeStamp = Utils.convertBytesToTimeStamp(mTimeStamp, 0, mTimeStamp.length,
            ByteOrder.LITTLE_ENDIAN);
        return Utils.convertTimeStampToUTCString(timeStamp * 1000);
    }

    public byte getSpecific() {
        return mSpecific;
    }

    public @SensorEventValueString String getValue() {
        if (mType == SensorEventType.DOOR) {
            int v = Utils.convertBytesToShort(mValue,0, SENSOR_EVENT_VALUE_LENGTH, ByteOrder.LITTLE_ENDIAN);
            if (v == SensorEventDoorValue.CLOSED) {
                return SensorEventValueString.DOOR_CLOSED;
            } else {
                return SensorEventValueString.DOOR_OPEN;
            }
        }

        return SensorEventValueString.UNKNOWN;
    }
}
