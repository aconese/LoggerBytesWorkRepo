package io.bytesatwork.skycell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class Utils {
    private static final String TAG = Utils.class.getSimpleName();
    static final protected char[] hexArray = "0123456789ABCDEF".toCharArray();

    //restrict instantiation
    private Utils() {
    }

    public static String getLineNumber() {
        return String.valueOf(Thread.currentThread().getStackTrace()[3].getLineNumber());
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

    public static int convertBytesToInt(byte[] value, int offset, int len, ByteOrder order) {
        //return (val[3] & 0xFF)<<24 | (val[2] & 0xFF)<<16 | (val[1] & 0xFF)<<8 | (val[0] & 0xFF);
        if (len == 2) {
            return ByteBuffer.wrap(value, offset, len).order(order).getShort();
        } else if (len == 4) {
            return ByteBuffer.wrap(value, offset, len).order(order).getInt();
        }

        return 0;
    }

    public static long convertBytesToLong(byte[] value, int offset, int len, ByteOrder order) {
        //return (val[3] & 0xFF)<<24 | (val[2] & 0xFF)<<16 | (val[1] & 0xFF)<<8 | (val[0] & 0xFF);
        if (len == 8) {
            return ByteBuffer.wrap(value, offset, len).order(order).getLong();
        }

        return 0;
    }

    public static String convertBytesToReadableHexString(byte[] value) {
        char[] hexChars = new char[value.length * 4];
        for ( int j = 0; j < value.length; j++ ) {
            int v = value[j] & 0xFF;
            hexChars[j * 4] = hexArray[v >>> 4];
            hexChars[j * 4 + 1] = hexArray[v & 0x0F];
            hexChars[j * 4 + 2] = ',';
            hexChars[j * 4 + 3] = ' ';
        }
        String tmp = new String(Arrays.copyOfRange(hexChars, 0, hexChars.length-2));
        return "["+tmp+"]";
    }

    public static long convertIntToUnsignedLong(int x) {
        return x & 0x00000000ffffffffL;
    }
}
