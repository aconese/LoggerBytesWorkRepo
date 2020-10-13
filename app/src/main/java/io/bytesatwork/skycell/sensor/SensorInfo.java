package io.bytesatwork.skycell.sensor;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import io.bytesatwork.skycell.ByteBufferParser;
import io.bytesatwork.skycell.Utils;
import android.util.Log;

public class SensorInfo {
    private static final String TAG = SensorState.class.getSimpleName();

    public static final int SENSOR_INFO_UID_LENGTH = 8;
    public static final int SENSOR_INFO_TYPE_LENGTH = 1;
    public static final int SENSOR_INFO_POSITION_LENGTH = 1;

    public enum SensorType {
        TEMPERATURE,
        AIR_PRESSURE,
        UNKNOWN,
    }


    private long mUID;
    private byte mPosition;
    public final SensorType type;

    private final static Map<Byte, String> POSITIONS =  new HashMap<Byte, String >(){{
        put((byte) 0, "ONBOARD");
        put((byte) 1, "BACKPLANE");
        put((byte) 2, "TOP");
        put((byte) 3, "BOTTOM");
        put((byte) 4, "LEFT");
        put((byte) 5, "RIGHT");
        put((byte) 6, "BACK");
        put((byte) 7, "INSIDE");
        put((byte) 255, "UNKNOWN");
    }};

    private final static Map<Byte, SensorType> TYPEVALUES =  new HashMap<Byte, SensorType >(){{
        put((byte) 0, SensorType.TEMPERATURE);
        put((byte) 1, SensorType.AIR_PRESSURE);
        put((byte) 255, SensorType.UNKNOWN);
    }};

    private final static Map<SensorType, String> TYPES =  new HashMap<SensorType, String >(){{
        put(SensorType.TEMPERATURE, "TEMPERATURE");
        put(SensorType.AIR_PRESSURE, "AIR_PRESSURE");
        put(SensorType.UNKNOWN, "");
    }};

    public static int getSensorInfoLength() {
        return SENSOR_INFO_UID_LENGTH +
                SENSOR_INFO_TYPE_LENGTH +
                SENSOR_INFO_POSITION_LENGTH;
    }

    private static SensorType parseType(byte type) {
        if (TYPEVALUES.containsKey(type)) {
            return TYPEVALUES.get(type);
        }
        Log.e(TAG+":"+ Utils.getLineNumber(), "Unknown Type: " + Integer.toHexString(type));

        return SensorType.UNKNOWN;
    }

    public SensorInfo(byte[] infoBuffer, ByteOrder order) {
        ByteBufferParser parser = new ByteBufferParser(infoBuffer);

        this.mUID = Utils.convertBytesToLong(parser.getNextBytes(SENSOR_INFO_UID_LENGTH), 0, SENSOR_INFO_UID_LENGTH, order);
        this.mPosition = parser.getNextByte();
        this.type = parseType(parser.getNextByte());
    }

    public String getUUID() {
        return Long.toHexString(mUID);
    }

    public String getPosition() {
        if (POSITIONS.containsKey(mPosition)) {
            return POSITIONS.get(mPosition);
        }
        Log.e(TAG+":"+ Utils.getLineNumber(), "Unknown Position: " + Integer.toHexString(mPosition));
        return "";
    }

    public String getType() {
        return TYPES.get(type);
    }
}
