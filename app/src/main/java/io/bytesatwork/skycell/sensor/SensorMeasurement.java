/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.sensor;

import io.bytesatwork.skycell.ByteBufferParser;
import io.bytesatwork.skycell.Utils;

import android.util.Log;

import java.nio.ByteOrder;

public class SensorMeasurement {
    private static final String TAG = SensorMeasurement.class.getSimpleName();
    private static final int SENSOR_MEASUREMENT_VALUE_ERROR = Short.MIN_VALUE;
    public static final String SENSOR_MEASUREMENT_VALUE_ERROR_STRING = "ERROR";

    public static final int SENSOR_MEASUREMENT_TIMESTAMP_LENGTH = 4;
    public static final int SENSOR_MEASUREMENT_VALUE_LENGTH = 2;

    private final byte[] mTimeStamp;
    public final byte[] mValues;

    private SensorMeasurement() {
        this.mTimeStamp = new byte[SENSOR_MEASUREMENT_TIMESTAMP_LENGTH];
        this.mValues = null;
    }

    public SensorMeasurement(byte[] dataBuffer, int numSensors) {
        Log.d(TAG+":"+ Utils.getLineNumber(), "dataBuffer len: "+dataBuffer.length);
        ByteBufferParser parser = new ByteBufferParser(dataBuffer);
        this.mTimeStamp = parser.getNextBytes(SENSOR_MEASUREMENT_TIMESTAMP_LENGTH);
        this.mValues = parser.getNextBytes(numSensors * SENSOR_MEASUREMENT_VALUE_LENGTH);
    }

    public static int getSensorMeasurementLength(int numSensors) {
        return SENSOR_MEASUREMENT_TIMESTAMP_LENGTH +
            numSensors * SENSOR_MEASUREMENT_VALUE_LENGTH;
    }

    public String getUTCTimeStamp() {
        long timeStamp = Utils.convertBytesToTimeStamp(mTimeStamp, 0, mTimeStamp.length,
            ByteOrder.LITTLE_ENDIAN);
        return Utils.convertTimeStampToUTCString(timeStamp);
    }

    public float[] getValues() {
        float[] values = new float[mValues.length / SENSOR_MEASUREMENT_VALUE_LENGTH];
        ByteBufferParser parser = new ByteBufferParser(mValues);
        for (int i = 0; i < values.length; ++i) {
            int v = Utils.convertBytesToShort(parser.getNextBytes(SENSOR_MEASUREMENT_VALUE_LENGTH),
                0, SENSOR_MEASUREMENT_VALUE_LENGTH, ByteOrder.LITTLE_ENDIAN);
            if (v == SENSOR_MEASUREMENT_VALUE_ERROR) {
                values[i] = Float.NEGATIVE_INFINITY; //return error
            } else {
                values[i] = (float) v / 10;
            }
        }
        return values;
    }
}
