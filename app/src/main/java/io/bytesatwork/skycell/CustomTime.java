/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.os.SystemClock;
import android.util.Log;

public class CustomTime {
    private static final String TAG = CustomTime.class.getSimpleName();

    private static final CustomTime mTime = new CustomTime();
    private long mCloudTimeStamp;
    private long mElapsedRealtimeStart;

    public static CustomTime getInstance() {
        return mTime;
    }

    private CustomTime() {
        this.mCloudTimeStamp = 0L;
        this.mElapsedRealtimeStart = 0L;
    }

    public long currentTimeMillis() {
        long elapsed = SystemClock.elapsedRealtime() - mElapsedRealtimeStart;
        long timeStamp = mCloudTimeStamp + elapsed;
        Log.i(TAG + ":" + Utils.getLineNumber(),
            "Elapsed " + elapsed + " since cloud ts: " + mCloudTimeStamp + ", current ts: " + timeStamp +
                ", system ts: " + System.currentTimeMillis());
        return timeStamp;
    }

    public void setCloudTimeStamp(long timeStamp) {
        mCloudTimeStamp = timeStamp;
        mElapsedRealtimeStart = SystemClock.elapsedRealtime();
    }
}
