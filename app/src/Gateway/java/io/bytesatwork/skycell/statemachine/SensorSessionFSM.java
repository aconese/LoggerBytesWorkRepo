package io.bytesatwork.skycell.statemachine;

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

    public void sendDataRequest() {
        mSensor.mBleService.sendReadData(mSensor.getAddress(), (System.currentTimeMillis() / 1000));
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
