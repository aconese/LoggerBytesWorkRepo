/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class CustomTime {
    private static final String TAG = CustomTime.class.getSimpleName();

    private static final CustomTime mTime = new CustomTime();
    private Duration mDuration;

    public static CustomTime getInstance() {
        return mTime;
    }

    private CustomTime() {
        this.mDuration = Duration.ZERO;
    }

    public long currentTimeMillis() {
        return Clock.offset(Clock.systemUTC(), mDuration).millis();
    }

    public void setCurrentTimeMillis(long timeStamp) {
        mDuration = Duration.between(Instant.now(), Instant.ofEpochMilli(timeStamp));
    }
}
