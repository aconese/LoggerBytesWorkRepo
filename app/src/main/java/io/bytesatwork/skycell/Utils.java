/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class Utils {
    private static final String TAG = Utils.class.getSimpleName();
    static final private char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    //restrict instantiation
    private Utils() {
    }

    public static String getLineNumber() {
        return String.valueOf(Thread.currentThread().getStackTrace()[3].getLineNumber());
    }

    public static boolean isGateway() {
        return (BuildConfig.FLAVOR.equals(Constants.FLAVOUR_GATEWAY));
    }

    public static byte[] convertObjectToBytes(Object object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }

    public static Object convertBytesToObject(byte[] value) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(value);
            ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        }
    }

    public static short convertBytesToShort(byte[] value, int offset, int len, ByteOrder order) {
        if (len == 2) {
            return ByteBuffer.wrap(value, offset, len).order(order).getShort();
        }

        return 0;
    }

    public static int convertBytesToInt(byte[] value, int offset, int len, ByteOrder order) {
        if (len == 2) {
            return convertBytesToShort(value, offset, len, order);
        } else if (len == 4) {
            return ByteBuffer.wrap(value, offset, len).order(order).getInt();
        }

        return 0;
    }

    public static long convertBytesToLong(byte[] value, int offset, int len, ByteOrder order) {
        if (len == 2) {
            return convertBytesToShort(value, offset, len, order);
        } else if (len == 4) {
            return convertBytesToInt(value, offset, len, order);
        } else if (len == 8) {
            return ByteBuffer.wrap(value, offset, len).order(order).getLong();
        }

        return 0;
    }

    public static byte[] convertShortToBytes(short value, ByteOrder order) {
        return ByteBuffer.allocate(2).order(order).putShort(value).array();
    }

    public static byte[] convertIntToBytes(int value, ByteOrder order) {
        return ByteBuffer.allocate(4).order(order).putInt(value).array();
    }

    public static byte[] convertLongToBytes(long value, ByteOrder order) {
        return ByteBuffer.allocate(8).order(order).putLong(value).array();
    }

    public static long convertBytesToTimeStamp(byte[] value, int offset, int len, ByteOrder order) {
        if (len == 2 || len == 4) {
            long timeStamp = convertBytesToInt(value, offset, len, order);
            return TimeUnit.SECONDS.toMillis(timeStamp) + Constants.EPOCH_OFFSET_MS;
        } else if (len == 8) {
            return convertBytesToLong(value, offset, len, order);
        }

        return 0;
    }

    public static String convertTimeStampToUTCString(long timeStamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeStamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.DATEFORMAT_UTC);
        dateFormat.setTimeZone(TimeZone.getTimeZone(Constants.TIMEZONE_UTC));
        return dateFormat.format(cal.getTime());
    }

    public static long convertUTCStringToTimeStamp(String timeStamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.DATEFORMAT_UTC);
        dateFormat.setTimeZone(TimeZone.getTimeZone(Constants.TIMEZONE_UTC));
        long ts = 0;
        try {
            ts = Objects.requireNonNull(dateFormat.parse(timeStamp)).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return ts;
    }

    public static String convertBytesToHexString(byte[] value) {
        char[] hexChars = new char[value.length * 2];
        for (int i = 0; i < value.length; ++i) {
            int v = value[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String convertBytesToHexString(byte[] value, int offset, int len, ByteOrder
        order, Class<?> dataType) {
        ByteBuffer buffer = ByteBuffer.wrap(value, offset, len).order(order);
        StringBuilder hexStringBuilder = new StringBuilder();

        if (dataType == Long.class) {
            while (buffer.remaining() > 0) {
                hexStringBuilder.append(String.format("%016X", (0xFFFFFFFFFFFFFFFFL & buffer.getLong())));
            }
            return hexStringBuilder.toString();
        } else if (dataType == Integer.class) {
            while (buffer.remaining() > 0) {
                hexStringBuilder.append(String.format("%08X", (0xFFFFFFFF & buffer.getInt())));
            }
            return hexStringBuilder.toString();
        } else if (dataType == Short.class) {
            while (buffer.remaining() > 0) {
                hexStringBuilder.append(String.format("%04X", (0xFFFF & buffer.getShort())));
            }
            return hexStringBuilder.toString();
        } else { //if (dataType == Byte.class)
            return convertBytesToHexString(value);
        }
    }

    public static String convertBytesToReadableHexString(byte[] value) {
        if (value.length > 0) {
            char[] hexChars = new char[value.length * 4];
            for (int i = 0; i < value.length; ++i) {
                int v = value[i] & 0xFF;
                hexChars[i * 4] = HEX_ARRAY[v >>> 4];
                hexChars[i * 4 + 1] = HEX_ARRAY[v & 0x0F];
                hexChars[i * 4 + 2] = ',';
                hexChars[i * 4 + 3] = ' ';
            }
            String tmp = new String(Arrays.copyOfRange(hexChars, 0, hexChars.length - 2));
            return "[" + tmp + "]";
        } else {
            return "[]";
        }
    }

    public static int indexOf(byte[] value, int from, byte search) {
        if (value.length > 0 && from < value.length) {
            for (int i = from; i < value.length; ++i) {
                if (value[i] == search) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static String convertBytesToString(byte[] value) {
        int len = indexOf(value, 0, (byte) 0x00);
        if (len < 0) {
            len = value.length;
        }
        return new String(value, 0, len, StandardCharsets.UTF_8);
    }

    public static byte[] convertStringToBytes(String value) {
        if (value.length() > 0) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        return Constants.EMPTY_BYTES;
    }

    public static int convertByteToUnsigned(byte b) {
        return b & 0xFF;
    }

    public static int convertShortToUnsigned(short s) {
        return s & 0xFFFF;
    }

    public static long convertIntToUnsigned(int i) {
        return i & 0xFFFFFFFFL;
    }

    public static BigInteger convertLongToUnsigned(long l) {
        return new BigInteger(1, convertLongToBytes(l, ByteOrder.BIG_ENDIAN));
    }
}
