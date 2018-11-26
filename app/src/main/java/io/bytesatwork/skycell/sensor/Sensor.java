package io.bytesatwork.skycell.sensor;

import io.bytesatwork.skycell.BuildConfig;
import io.bytesatwork.skycell.SynchronizedByteBuffer;
import io.bytesatwork.skycell.Utils;
import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.statemachine.SensorSessionFSM;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sensor {
    private static final String TAG = Sensor.class.getSimpleName();

    private final String mAddress;
    private short mReadPosition;
    private SensorDataHandler mDataHandler;
    public SensorSessionFSM mSensorSessionFSM;
    public static BleService mBleService;

    //state
    public SynchronizedByteBuffer mStateBuffer;
    public SensorState mState;
    private boolean mStateComplete;
    //data
    public SynchronizedByteBuffer mDataBuffer;
    private List<SensorMeasurement> mData;
    private boolean mDataComplete;
    //extrema
    public SynchronizedByteBuffer mExtremaBuffer;
    private List<SensorExtrema> mExtrema;
    private boolean mExtremaComplete;

    private Sensor() {
        this.mAddress = "";
        this.mStateBuffer = null;
        this.mState = null;
        this.mStateComplete = false;
        this.mDataBuffer = null;
        this.mData = null;
        this.mDataComplete = false;
        this.mExtremaBuffer = null;
        this.mExtrema = null;
        this.mExtremaComplete = false;
        this.mDataHandler = null;
        this.mSensorSessionFSM = null;
    }

    public Sensor(BleService bleService, String addr) {
        this.mAddress = addr;
        this.mStateBuffer = new SynchronizedByteBuffer(SensorState.getSensorStateLength());
        this.mState = new SensorState();
        this.mStateComplete = false;
        this.mDataBuffer = null;
        this.mData = Collections.synchronizedList(new ArrayList<SensorMeasurement>());
        this.mDataComplete = false;
        this.mExtremaBuffer = new SynchronizedByteBuffer(SensorExtrema.getSensorExtremaLength());
        this.mExtrema = Collections.synchronizedList(new ArrayList<SensorExtrema>());
        this.mExtremaComplete = false;
        this.mDataHandler = new SensorDataHandler(this);
        if (Utils.isGateway()) {
            this.mSensorSessionFSM = new SensorSessionFSM(this);
            this.mBleService = bleService;
        } else {
            this.mSensorSessionFSM = null;
            this.mBleService = null;
        }
    }

    public boolean parseState() {
        if (mStateBuffer.remaining() == 0) {
            if (mState.parseState(mStateBuffer.array())) {
                mDataBuffer = new SynchronizedByteBuffer(
                    SensorMeasurement.getSensorMeasurementLength(mState.getNumSensors()));
                return true;
            }
        }

        return false;
    }

    public void completeState() {
        mStateComplete = true;
    }

    public boolean isStateCompleted() {
        return mStateComplete;
    }

    public boolean clearState() {
        mStateBuffer.clear();
        mStateComplete = false;
        return true;
    }

    public boolean parseData() {
        if (mDataBuffer != null && mDataBuffer.remaining() == 0) {
            SensorMeasurement sensorMeasurement = new SensorMeasurement(mDataBuffer.array(),
                mState.getNumSensors());
            return mData.add(sensorMeasurement);
        }

        return false;
    }

    public void completeData() {
        mDataComplete = true;
    }

    public boolean isDataCompleted() {
        return mDataComplete;
    }

    public boolean clearData() {
        mDataBuffer.clear();
        mData.clear();
        mDataComplete = false;
        return true;
    }

    public void setReadPosition(short readPosition) {
        this.mReadPosition = readPosition;
    }

    public short getReadPosition() {
        return mReadPosition;
    }

    public boolean parseExtrema() {
        if (mExtremaBuffer.remaining() == 0) {
            SensorExtrema sensorExtrema = new SensorExtrema(mExtremaBuffer.array());
            return mExtrema.add(sensorExtrema);
        }

        return false;
    }

    public void completeExtrema() {
        mExtremaComplete = true;
    }

    public boolean isExtremaCompleted() {
        return mExtremaComplete;
    }

    public boolean clearExtrema() {
        mExtremaBuffer.clear();
        mExtrema.clear();
        mExtremaComplete = false;
        return true;
    }

    public String getDeviceId() {
        return mState.getDeviceId();
    }

    public String getAddress() {
        return mAddress;
    }

    public String getUTCReadoutString() {
        return Utils.convertTimeStampToUTCString(System.currentTimeMillis());
    }

    public String convertToJSONString() {
        JSONObject object = new JSONObject();
        try {
            JSONObject sensor = new JSONObject();
                sensor.put("ContainerId", mState.getContainerId());
                sensor.put("DeviceId", mState.getDeviceId());
                sensor.put("SoftwareVersion", mState.getSoftwareVersion());
                sensor.put("HardwareVersion", mState.getHardwareVersion());
                sensor.put("CurrentIntervalSec", mState.getInterval());
                sensor.put("BatteryState", mState.getBattery());
                sensor.put("NumSensors", mState.getNumSensors());
            object.put("SensorDevice", sensor);
            JSONObject readout = new JSONObject();
                readout.put("TimestampUTC", getUTCReadoutString());
                JSONObject gateway = new JSONObject();
                    gateway.put("Id", "1234"); //TODO: Define unique id
                    gateway.put("Type", BuildConfig.FLAVOR);
                    gateway.put("SoftwareVersion", "v" + BuildConfig.VERSION_NAME);
                    JSONObject position = new JSONObject(); //TODO: Get gps position
                        position.put("Latitude", new Double(47.4951597));
                        position.put("Longitude", new Double(8.7156737));
                        position.put("Altitude", new Double(446));
                gateway.put("Position", position);
                readout.put("Gateway", gateway);
                JSONArray extrema = new JSONArray();
                synchronized (mExtrema) {
                    for (SensorExtrema sensorExtrema : mExtrema) {
                        JSONObject minmax = new JSONObject();
                        minmax.put("Type", sensorExtrema.getType());
                        minmax.put("TimestampUTC", sensorExtrema.getUTCTimeStamp());
                        minmax.put("PeriodStartTimestampUTC",
                            sensorExtrema.getUTCPeriodStartTimeStamp());
                        minmax.put("SensorId", sensorExtrema.getSensorId());
                        minmax.put("Value", sensorExtrema.getValue());
                        minmax.put("BinaryData", sensorExtrema.getBinaryData());
                        minmax.put("Signature", sensorExtrema.getSignature());
                        extrema.put(minmax);
                    }
                }
                readout.put("Extrema", extrema);
                JSONArray data = new JSONArray();
                synchronized (mData) {
                    for (SensorMeasurement sensorMeasurement : mData) {
                        JSONObject measurement = new JSONObject();
                        measurement.put("TimestampUTC", sensorMeasurement.getUTCTimeStamp());
                        JSONArray values = new JSONArray();
                        for (String sensorMeasurementValue : sensorMeasurement.getValues()) {
                            values.put(sensorMeasurementValue);
                        }
                        measurement.put("Values", values);
                        data.put(measurement);
                    }
                }
                readout.put("Data", data);
            object.put("Readout", readout);
            //Log.i(TAG + ":" + Utils.getLineNumber(), "JSON:\n"+object.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object.toString();
    }

    public boolean upload() {
        return mDataHandler.upload();
    }

    public void close() {
        if (mSensorSessionFSM != null) {
            mSensorSessionFSM.signalDisconnected();
        }
    }
}
