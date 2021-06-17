/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.statemachine;

import android.util.Log;

import io.bytesatwork.skycell.CustomTime;
import io.bytesatwork.skycell.SkyCellService;
import io.bytesatwork.skycell.Utils;

public class ConnectivityFSM {
    private static final String TAG = ConnectivityFSM.class.getSimpleName();

    private final SkyCellService mSkyCellService;
    private final ConnectivityFSMContext mConnectivityFSMContext;

    public ConnectivityFSM(SkyCellService skyCellService) {
        this.mSkyCellService = skyCellService;
        this.mConnectivityFSMContext = new ConnectivityFSMContext(this);
        this.mConnectivityFSMContext.enterStartState();
    }

    /* Signals */
    public void signalBleOff() { mConnectivityFSMContext.bleOff(); }
    public void signalBleOn() { mConnectivityFSMContext.bleOn(); }
    public void signalWifiOff() { mConnectivityFSMContext.wifiOff(); }
    public void signalWifiOn() { mConnectivityFSMContext.wifiOn(); }
    public void signalCloudOff() { mConnectivityFSMContext.cloudOff(); }
    public void signalCloudOn() { mConnectivityFSMContext.cloudOn(); }
    public void signalValidTime() { mConnectivityFSMContext.validTime(); }

    /* Entry actions */
    public void off() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "OFF entered");
    }
    public void wifiOffBleOn() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "WIFI OFF, BLE ON entered");
    }

    public void wifiOnBleOff() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "WIFI ON, BLE OFF entered");
    }

    public void on() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "ON entered");
    }

    public void checkTime() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "ON CLOUD entered");
        if (CustomTime.getInstance().hasValidTime()) {
            Log.d(TAG + ":" + Utils.getLineNumber(), "Got a valid cloud time");
            signalValidTime();
        }
    }

    public void startAdvertising() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "ON, VALID TIME entered");
        mSkyCellService.startAdvertising();
    }

    /* Exit actions */
    public void stopAdvertising() {
        Log.i(TAG + ":" + Utils.getLineNumber(), "ON, VALID TIME exited");
        mSkyCellService.stopAdvertising();
    }
}
