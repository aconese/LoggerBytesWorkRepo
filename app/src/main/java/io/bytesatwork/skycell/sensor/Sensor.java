package io.bytesatwork.skycell.sensor;

import android.os.Environment;
import android.util.Log;

import io.bytesatwork.skycell.BuildConfig;
import io.bytesatwork.skycell.Constants;
import io.bytesatwork.skycell.SkyCellApplication;
import io.bytesatwork.skycell.SynchronizedByteBuffer;
import io.bytesatwork.skycell.Utils;
import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.statemachine.SensorSessionFSM;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteOrder;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sensor {
    private static final String TAG = Sensor.class.getSimpleName();
    private SkyCellApplication app;
    private final String mAddress;
    private short mReadPosition;
    public SensorSessionFSM mSensorSessionFSM;
    public static BleService mBleService;

    //state
    public SynchronizedByteBuffer mStateBuffer;
    public SensorState mState;
    private boolean mStateComplete;
    public SynchronizedByteBuffer mSensorInfoBuffer;
    private boolean mInfosComplete;
    //data
    public SynchronizedByteBuffer mDataBuffer;
    private List<SensorMeasurement> mData;
    private boolean mDataComplete;
    //extrema
    public SynchronizedByteBuffer mExtremaBuffer;
    private List<SensorExtrema> mExtrema;
    private boolean mExtremaComplete;

    private Sensor() {
        this.app = null;
        this.mAddress = "";
        this.mStateBuffer = null;
        this.mSensorInfoBuffer = null;
        this.mState = null;
        this.mStateComplete = false;
        this.mInfosComplete = false;
        this.mDataBuffer = null;
        this.mData = null;
        this.mDataComplete = false;
        this.mExtremaBuffer = null;
        this.mExtrema = null;
        this.mExtremaComplete = false;
        this.mSensorSessionFSM = null;
    }

    public Sensor(BleService bleService, String addr) {
        this.app = ((SkyCellApplication) SkyCellApplication.getAppContext());
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

    public boolean parseInfos() {
        if (mSensorInfoBuffer.remaining() == 0) {
            if (mState.parseSensorInfos(mSensorInfoBuffer.array(), ByteOrder.LITTLE_ENDIAN)) {
                return true;
            }
        }
        return false;
    }

    public void completeState() {
        mStateComplete = true;
        mSensorInfoBuffer = new SynchronizedByteBuffer(
                mState.getSensorInfosLength());
    }

    public boolean isStateCompleted() {
        return mStateComplete;
    }

    public void completeInfos() {
        mInfosComplete = true;
    }

    public boolean areInfosCompleted() {
        return mInfosComplete;
    }

    public boolean clearState() {
        mStateBuffer.clear();
        mStateComplete = false;
        return true;
    }

    public boolean clearInfos() {
        mSensorInfoBuffer.clear();
        mInfosComplete = false;
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
            JSONObject readout = new JSONObject();
            readout.put("containerSerialNumber", mState.getContainerId());
            readout.put("loggerNumber", mState.getDeviceId());
            readout.put("softwareVersion", mState.getSoftwareVersion());
            readout.put("hardwareVersion", mState.getHardwareVersion());
            readout.put("loggerTransmissionRateSeconds", mState.getInterval());
            readout.put("batteryVoltage", mState.getBattery());
            readout.put("sensorQuantity", mState.getNumSensors());
            readout.put("readOutUtc", getUTCReadoutString());
            readout.put("upTimeSeconds", 0); // TODO what is this?
            readout.put("gatewayNumber", "1234"); // TODO get unique ID
            readout.put("sensingFrequencyMilliseconds", 0); // TODO what is this?

            JSONArray sensors = new JSONArray();
            synchronized (mState.mSensorInfos) {
                for (SensorInfo info : mState.mSensorInfos) {
                    JSONObject sensor = new JSONObject();
                    sensor.put("sensorNumber", info.getUUID());
                    sensor.put("sensorType", info.getType());
                    sensor.put("sensorPosition", info.getPosition());
                    sensors.put(sensor);
                }
                readout.put("sensors", sensors);
            }
            object.put("readout", readout);

            JSONObject loggerEvents = new JSONObject();
            JSONArray extremas = new JSONArray(); // TODO
            loggerEvents.put("extremas", extremas);

            JSONArray doorEvents = new JSONArray(); // TODO
            loggerEvents.put("doorEvents", doorEvents);

            JSONArray accelerationEvents = new JSONArray(); // TODO
            loggerEvents.put("accelerationEvents", accelerationEvents);

            JSONArray measurements = new JSONArray();
            synchronized (mData) {
                for (SensorMeasurement sensorMeasurement : mData) {
                    JSONArray data = new JSONArray();
                    JSONObject measurement = new JSONObject();
                    measurement.put("timestamp", sensorMeasurement.getUTCTimeStamp());

                    String[] values = sensorMeasurement.getValues();
                    for (int i = 0; i < values.length; i++) {
                        JSONObject sensorData = new JSONObject();
                        sensorData.put("sensorNumber", mState.mSensorInfos.get(i).getUUID());
                        sensorData.put("data", values[i]);
                        data.put(sensorData);
                    }
                    measurement.put("data", data);
                    measurements.put(measurement);
                }
            }
            loggerEvents.put("measurements", measurements);
            object.put("loggerEvents", loggerEvents);

            //Log.i(TAG + ":" + Utils.getLineNumber(), "JSON:\n"+object.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String json;
        try {
            json = object.toString(2);
        } catch (JSONException e) {
            json = object.toString();
        }
        return json;
    }

    public boolean writeToFile() {
        String fileName = getAddress() + "_" + System.currentTimeMillis() + Constants.FILE_ENDING;
        String json = convertToJSONString();
        boolean ok = false;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(app.getApplicationContext().getExternalFilesDir(null)
                .getAbsolutePath(), fileName);
            Log.i(TAG + ":" + Utils.getLineNumber(), "Write: File: " + file.getAbsolutePath());
            try {
                //Open file
                FileOutputStream fos = new FileOutputStream(file, false);

                //Get file lock
                FileLock lock = fos.getChannel().lock();

                //Write
                fos.write(json.getBytes());
                fos.flush();

                //Release lock
                lock.release();

                //Close file
                fos.close();
                ok = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ok;
    }

    public void close() {
        if (mSensorSessionFSM != null) {
            mSensorSessionFSM.signalDisconnected();
        }
    }
}
