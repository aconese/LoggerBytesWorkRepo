package io.bytesatwork.skycell;

import java.util.Arrays;

public class ByteBufferParser {
    private static final String TAG = ByteBufferParser.class.getSimpleName();

    private int mParseIndex;
    private byte[] mBuffer;

    public ByteBufferParser(byte[] buffer) {
        this.mParseIndex = 0;
        this.mBuffer = buffer;
    }

    public byte getNextByte() {
        int at = mParseIndex;
        ++mParseIndex;

        if (at < mBuffer.length) {
            return mBuffer[at];
        }

        return 0;
    }

    public byte[] getNextBytes(int num) {
        int from = mParseIndex;
        mParseIndex += num;

        if (mParseIndex <= mBuffer.length) { //to is exclusive
            return Arrays.copyOfRange(mBuffer, from, mParseIndex);
        }

        return Constants.EMPTY_BYTES;
    }
}