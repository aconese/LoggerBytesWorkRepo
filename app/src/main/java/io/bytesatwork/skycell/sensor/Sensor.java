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
        this.mState = null;
        this.mStateComplete = false;
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
            sensor.put("containerId", mState.getContainerId());
            sensor.put("deviceId", mState.getDeviceId());
            sensor.put("softwareVersion", mState.getSoftwareVersion());
            sensor.put("hardwareVersion", mState.getHardwareVersion());
            sensor.put("currentIntervalSec", mState.getInterval());
            sensor.put("batteryState", mState.getBattery());
            sensor.put("numSensors", mState.getNumSensors());
            object.put("sensorDevice", sensor);
            JSONObject readout = new JSONObject();
            readout.put("timestampUTC", getUTCReadoutString());
            JSONObject gateway = new JSONObject();
            gateway.put("id", "1234"); //TODO: Define unique id
            gateway.put("type", BuildConfig.FLAVOR);
            gateway.put("softwareVersion", "v" + BuildConfig.VERSION_NAME);
            JSONObject position = new JSONObject(); //TODO: Get gps position
            position.put("latitude", new Double(47.4951597));
            position.put("longitude", new Double(8.7156737));
            position.put("altitude", new Double(446));
            gateway.put("position", position);
            readout.put("gateway", gateway);
            JSONArray extrema = new JSONArray();
            synchronized (mExtrema) {
                for (SensorExtrema sensorExtrema : mExtrema) {
                    JSONObject minmax = new JSONObject();
                    minmax.put("type", sensorExtrema.getType());
                    minmax.put("timestampUTC", sensorExtrema.getUTCTimeStamp());
                    minmax.put("periodStartTimestampUTC",
                        sensorExtrema.getUTCPeriodStartTimeStamp());
                    minmax.put("sensorId", sensorExtrema.getSensorId());
                    minmax.put("value", sensorExtrema.getValue());
                    minmax.put("binaryData", sensorExtrema.getBinaryData());
                    minmax.put("signature", sensorExtrema.getSignature());
                    extrema.put(minmax);
                }
            }
            readout.put("extrema", extrema);
            JSONArray data = new JSONArray();
            synchronized (mData) {
                for (SensorMeasurement sensorMeasurement : mData) {
                    JSONObject measurement = new JSONObject();
                    measurement.put("timestampUTC", sensorMeasurement.getUTCTimeStamp());
                    JSONArray values = new JSONArray();
                    for (String sensorMeasurementValue : sensorMeasurement.getValues()) {
                        values.put(sensorMeasurementValue);
                    }
                    measurement.put("values", values);
                    data.put(measurement);
                }
            }
            readout.put("data", data);
            object.put("readout", readout);
            //Log.i(TAG + ":" + Utils.getLineNumber(), "JSON:\n"+object.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object.toString();
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
