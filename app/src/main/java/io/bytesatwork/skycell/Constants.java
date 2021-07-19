/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.os.ParcelUuid;
import java.util.UUID;

public final class Constants {

    //restrict instantiation
    private Constants() {
    }

    public static final byte[] EMPTY_BYTES = {};
    public static final String TIMEZONE_UTC = "UTC";
    public static final String DATEFORMAT_UTC = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String FILE_ENDING = ".json";
    public static final long UPLOAD_RATE = 60;

    //FLAVOUR
    public static final String FLAVOUR_GATEWAY = "Gateway";
    public static final String FLAVOUR_MOBILE = "Mobile";

    //Gateway STATUS
    public static final String GATEWAY_STATUS_OFFLINE = "OFFLINE";
    public static final String GATEWAY_STATUS_ONLINE = "ONLINE";

    //BLE
    public static final String SKYCELL_DEVICE_NAME = "SkyCell";
    public static final int REQUEST_ENABLE_BT = 1;
    public static final String SKYCELL_SENSORS = "io.bytesatwork.skycell.SKYCELL_SENSORS";

    public static final String SKYCELL_SERVICE_16 = "1543";
    public static final String SKYCELL_SERVICE = "00001543-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_SERVICE_UUID = UUID.fromString(SKYCELL_SERVICE);
    public static final ParcelUuid SKYCELL_SERVICE_PARCELUUID = new ParcelUuid(SKYCELL_SERVICE_UUID);

    public static final String SKYCELL_CHAR_CMD = "00001544-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_CMD_UUID = UUID.fromString(SKYCELL_CHAR_CMD);
    public static final String SKYCELL_CHAR_STATE = "00001545-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_STATE_UUID = UUID.fromString(SKYCELL_CHAR_STATE);
    public static final String SKYCELL_CHAR_DATA = "00001546-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_DATA_UUID = UUID.fromString(SKYCELL_CHAR_DATA);
    public static final String SKYCELL_CHAR_EXTREMA = "00001547-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_EXTREMA_UUID = UUID.fromString(SKYCELL_CHAR_EXTREMA);
    public static final String SKYCELL_CHAR_EVENT = "00001548-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_CHAR_EVENT_UUID = UUID.fromString(SKYCELL_CHAR_EVENT);

    public static final String SKYCELL_DESC_CCC = "00002902-0000-1000-8000-00805f9b34fb";
    public static final UUID SKYCELL_DESC_CCC_UUID = UUID.fromString(SKYCELL_DESC_CCC);

    //Service intent filter priorities
    public static final int SKYCELL_INTENT_FILTER_HIGH_PRIORITY = 10;
    public static final int SKYCELL_INTENT_FILTER_LOW_PRIORITY = 0;

    //Commands
    public static final byte CMD_CONFIG = (byte) 0xC0;
    public static final byte CMD_START_TRIP = (byte) 0xC1;
    public static final byte CMD_END_TRIP = (byte) 0xC2;
    public static final byte CMD_GET_STATE = (byte) 0xC3;
    public static final byte CMD_READ_DATA = (byte) 0xC4;
    public static final byte CMD_READ_CURRENT_DATA = (byte) 0xC5;
    public static final byte CMD_READ_CURRENT_EXTREMA = (byte) 0xC6;
    public static final byte CMD_READ_EXTREMA = (byte) 0xC7;
    public static final byte CMD_READ_EVENT = (byte) 0xC8;
    public static final byte CMD_CLEAR = (byte) 0xCC;
    public static final byte CMD_DISCONNECT = (byte) 0xCD;
    public static final byte CMD_DECOMMISSION = (byte) 0xCF;

    //Cmd Acknowledge
    public static final short ACK_CMD_OK = 0x00A0;
    public static final short ACK_CMD_UNKNOWN = 0x00A1;
    public static final short ACK_CMD_PARAM_UNKNOWN = 0x00A2;
    public static final short ACK_CMD_PENDING = 0x00A3;
    public static final short ACK_UNKNOWN = 0x00A4;
}
