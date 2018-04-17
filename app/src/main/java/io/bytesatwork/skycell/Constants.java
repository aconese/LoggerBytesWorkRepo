package io.bytesatwork.skycell;

import android.os.ParcelUuid;

import java.util.UUID;

public final class Constants {

    //restrict instantiation
    private Constants() {
    }

    public static final String SKYCELL_DEVICE_NAME = "SkyCell";
    public static final int REQUEST_ENABLE_BT = 1;
    public static final String SKYCELL_SENSORS = "io.bytesatwork.skycell.SKYCELL_SENSORS";

    public static final String SKYCELL_SERVICE_16 = "1543";
    public static final String SKYCELL_SERVICE = "00001543-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_SERVICE_UUID = UUID.fromString(SKYCELL_SERVICE);
    public static final ParcelUuid SKYCELL_SERVICE_PARCELUUID = new ParcelUuid(SKYCELL_SERVICE_UUID);

    public static final String SKYCELL_CHAR_CMD = "00001544-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_CMD_UUID = UUID.fromString(SKYCELL_CHAR_CMD);
    public static final String SKYCELL_CHAR_DATA = "00001545-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_DATA_UUID = UUID.fromString(SKYCELL_CHAR_DATA);
    public static final String SKYCELL_CHAR_STATE = "00001546-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_STATE_UUID = UUID.fromString(SKYCELL_CHAR_STATE);

    public static final String SKYCELL_DESC_CCC = "00002902-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_DESC_CCC_UUID = UUID.fromString(SKYCELL_DESC_CCC);

    //Service intent filter priorities
    public static final int SKYCELL_INTENT_FILTER_HIGH_PRIORITY = 10;
    public static final int SKYCELL_INTENT_FILTER_LOW_PRIORITY = 0;

    //Commands
    public static final byte[] EMPTY_BYTES = {};
    public static final byte CMD_CONFIG = (byte) 0xC0;
    public static final byte CMD_READ = (byte) 0xC1;
    public static final byte CMD_CLEAR = (byte) 0xC2; //Read Ack
    public static final byte CMD_GET_STATE = (byte) 0xC3;
    public static final byte CMD_DISCONNECT = (byte) 0xC4;

    //Cmd Acknowledge
    public static final byte ACK_CMD_OK = (byte) 0xA0;
    public static final byte ACK_CMD_UNKNOWN = (byte) 0xA1;
    public static final byte ACK_CMD_PARAM_UNKNOWN = (byte) 0xA2;
    public static final byte ACK_CMD_PENDING = (byte) 0xA3;
    public static final byte ACK_UNKNOWN = (byte) 0xA4;

    /*public static enum Command {
        CONFIG((byte) 0xC0),
        READ((byte) 0xC1),
        READ_ACK((byte) 0xC2),
        GET_STATE((byte) 0xC3),
        DISCONNECT((byte) 0xC4);

        private byte cmd;

        private Command(byte cmd) {
            this.cmd = (byte) cmd;
        }

        public byte getCommand() {
            return cmd;
        }

        public void setCommand(byte cmd) {
            this.cmd = cmd;
        }

        public String toString() {
            return Byte.toString(cmd);
        }
    }*/
}
