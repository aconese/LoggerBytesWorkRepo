/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import java.nio.ByteBuffer;

public class SynchronizedByteBuffer {
    private final ByteBuffer mBuffer;

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
