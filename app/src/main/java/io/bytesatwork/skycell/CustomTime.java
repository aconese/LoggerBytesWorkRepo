/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

public class CustomTime {
    private static final String TAG = CustomTime.class.getSimpleName();

    private static final CustomTime mTime = new CustomTime();
    private long mTimeDiffMillis;

    public static CustomTime getInstance() {
        return mTime;
    }

    private CustomTime() {
        this.mTimeDiffMillis = 0;
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis() + mTimeDiffMillis;
    }

    public void setCurrentTimeMillis(long timeStamp) {
        mTimeDiffMillis = timeStamp - System.currentTimeMillis();
    }
}
