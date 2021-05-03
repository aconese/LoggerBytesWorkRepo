/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.sensor;

import androidx.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.bytesatwork.skycell.Utils;

public class SensorList {
    private static final String TAG = SensorList.class.getSimpleName();

    private List<Sensor> mSensorList;

    public SensorList() {
        this.mSensorList = Collections.synchronizedList(new ArrayList<Sensor>());
    }

    public SensorList(ArrayList<Sensor> sensorList) {
        this.mSensorList = sensorList;
    }

    public boolean addSensor(final Sensor sensor) {
        synchronized (mSensorList) {
            return mSensorList.add(sensor);
        }
    }

    public boolean removeSensor(final Sensor sensor) {
        synchronized (mSensorList) {
            return mSensorList.remove(sensor);
        }
    }

    public boolean removeSensor(final int index) {
        synchronized (mSensorList) {
            try {
                mSensorList.remove(index);
            } catch (IndexOutOfBoundsException e) {
                return false;
            }

            return true;
        }
    }

    @Nullable
    public Sensor getSensor(final int index) {
        synchronized (mSensorList) {
            Sensor s;

            try {
                s = mSensorList.get(index);
            } catch (IndexOutOfBoundsException e){
                return null;
            }

            return s;
        }
    }

    @Nullable
    public Sensor getSensorByAddress(final String addr) {
        synchronized (mSensorList) {
            for (Sensor s :  mSensorList) {
                if (s.getAddress().equals(addr)) {
                    Log.d(TAG+":"+ Utils.getLineNumber(), s.getAddress()+" == "+addr);
                    return s;
                }
            }
            return null;
        }
    }

    @Nullable
    public Sensor getSensorByDeviceId(final String did) {
        synchronized (mSensorList) {
            for (Sensor s :  mSensorList) {
                if (s.getDeviceId() == did) {
                    return s;
                }
            }
            return null;
        }
    }

    public int getSensorIndex(final Sensor sensor) {
        synchronized (mSensorList) {
            return mSensorList.indexOf(sensor);
        }
    }

    public int getSensorListCount() {
        synchronized (mSensorList) {
            return mSensorList.size();
        }
    }

    public void clearSensorList() {
        synchronized (mSensorList) {
            mSensorList.clear();
        }
    }
}
