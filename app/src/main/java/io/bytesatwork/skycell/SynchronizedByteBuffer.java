package io.bytesatwork.skycell;

import java.nio.ByteBuffer;

public class SynchronizedByteBuffer {
    private ByteBuffer mBuffer;

    public SynchronizedByteBuffer(int length) {
        this.mBuffer = ByteBuffer.allocate(length);
    }

    public synchronized int remaining() {
        return mBuffer.remaining();
    }

    public synchronized void put(byte[] value) {
        mBuffer.put(value);
    }

    public synchronized byte[] array() {
        return mBuffer.array();
    }

    public synchronized void clear() {
        mBuffer.clear();
    }
}
