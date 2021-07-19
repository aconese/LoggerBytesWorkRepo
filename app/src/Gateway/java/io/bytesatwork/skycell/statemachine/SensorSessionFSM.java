/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell.statemachine;

import io.bytesatwork.skycell.CustomTime;
import io.bytesatwork.skycell.sensor.Sensor;

public class SensorSessionFSM {
    private static final String TAG = Sensor.class.getSimpleName();

    private Sensor mSensor;
    private SensorSessionFSMContext mSensorSessionFSMContext;

    public SensorSessionFSM(Sensor sensor) {
        this.mSensor = sensor;
        this.mSensorSessionFSMContext = new SensorSessionFSMContext(this);
        this.mSensorSessionFSMContext.enterStartState();
    }

    /* Signals */
    public void signalTimeout() { mSensorSessionFSMContext.timeout(); }
    public void signalRejected() { mSensorSessionFSMContext.rejected(); }
    public void signalAccepted() { mSensorSessionFSMContext.accepted(); }
    public void signalDisconnected() { mSensorSessionFSMContext.disconnected(); }
    public void signalConnected() { mSensorSessionFSMContext.connected(); }
    public void signalDone() { mSensorSessionFSMContext.done(); }

    public void signalAck() {} //TODO: implement
    public void signalStateComplete() { mSensorSessionFSMContext.completedStatus(); }
    public void signalData() { mSensorSessionFSMContext.receiveDataPackage(); }
    public void signalDataComplete() { mSensorSessionFSMContext.completedData(); }
    public void signalExtrema() { mSensorSessionFSMContext.receiveExtremaPackage(); }
    public void signalExtremaComplete() { mSensorSessionFSMContext.completedExtrema(); }
    public void signalEvent() { mSensorSessionFSMContext.receiveEventPackage(); }
    public void signalEventComplete() { mSensorSessionFSMContext.completedEvent(); }

    /* Entry actions */
    public void checkConnection() {
        //TODO: implement
        signalAccepted();
    }

    public void sendStatusRequest() {
        mSensor.mBleService.sendGetState(mSensor.getAddress());
    }

    public void checkStatus() {
        //TODO: implement
        signalAccepted();
    }

    public void sendExtremaRequest() {
        mSensor.mBleService.sendReadExtrema(mSensor.getAddress());
    }

    public void sendEventRequest() {
        mSensor.mBleService.sendReadEvent(mSensor.getAddress());
    }

    public void sendDataRequest() {
        mSensor.mBleService.sendReadData(mSensor.getAddress(), (CustomTime.getInstance().currentTimeMillis() / 1000));
    }

    public void resetTimeout() {
        //TODO: implement
    }

    public void writeToFile() {
        if (mSensor.writeToFile()) {
            mSensor.mBleService.sendClear(mSensor.getAddress());
        }
        signalDone(); //disconnect
    }

    public void sendDisconnectRequest() {
        mSensor.mBleService.sendDisconnect(mSensor.getAddress());
    }

    public void resetNetwork() {
        //TODO: implement
        signalDone();
    }
}
