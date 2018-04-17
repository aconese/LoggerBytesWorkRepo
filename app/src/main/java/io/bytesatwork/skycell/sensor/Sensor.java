package io.bytesatwork.skycell.sensor;

import io.bytesatwork.skycell.Utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class Sensor {
    private static final String TAG = Sensor.class.getSimpleName();

    private final String mAddress;
    //state
    public SensorState mState;
    public ByteBuffer mStateBuffer;
    //data
    public ByteBuffer mPartDataBuffer;
    public List<SensorPartData> mData;

    private Sensor() {
        this.mAddress = "";
        this.mState = null;
        this.mStateBuffer = null;
        this.mPartDataBuffer = null;
        this.mData = null;
    }

    public Sensor(String addr) {
        this.mAddress = addr;
        this.mState = new SensorState();
        this.mStateBuffer = ByteBuffer.allocate(24); //TODO: calc size
        this.mPartDataBuffer = ByteBuffer.allocate(9); //TODO: calc size
        this.mData = new ArrayList<SensorPartData>();
    }

    public boolean parseState() {
        if (this.mStateBuffer.remaining() == 0) {
            byte[] stateBuffer = this.mStateBuffer.array();
            return mState.parseState(stateBuffer);
        }

        return false;
    }

    public boolean clearState() {
        this.mStateBuffer.clear();
        return true;
    }

    public boolean parsePartData() {
        if (this.mPartDataBuffer.remaining() == 0) {
            byte[] partDataBuffer = this.mPartDataBuffer.array();
            SensorPartData spd = new SensorPartData(partDataBuffer);
            return mData.add(spd);
        }

        return false;
    }

    public boolean clearData() {
        this.mPartDataBuffer.clear();
        this.mData.clear();
        return true;
    }


    public int getId() {
        return mState.getId();
    }

    public String getIdString() {
        return mState.getIdString();
    }

    public String getAddress() {
        return mAddress;
    }

    public String getUTCReadoutString() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(cal.getTime());
    }

    public String convertToJSONString() {
        JSONObject object = new JSONObject();
        try {
            object.put("SensorId", this.getIdString());
                JSONObject start = new JSONObject();
                start.put("TimestampUTC", this.mState.getUtcStartString());
            object.put("Start", start);
                JSONObject readout = new JSONObject();
                readout.put("TimestampUTC", getUTCReadoutString());
                    JSONObject position = new JSONObject();
                    position.put("Latitude", new Double(47.4951597));
                    position.put("Longitude", new Double(8.7156737));
                    position.put("Altitude", new Double(446));
                readout.put("Position", position);
                    JSONObject gateway = new JSONObject();
                    gateway.put("Id", "1234");
                    gateway.put("Type", "Mobile");
                    gateway.put("FirmwareVersion", "2.0.1");
                readout.put("Gateway", gateway);
            object.put("Readout", readout);
                JSONArray data = new JSONArray();
                for (SensorPartData spd : this.mData) {
                    JSONObject partData = new JSONObject();
                        JSONObject measurement = new JSONObject();
                        measurement.put("TimeDiff", spd.getTimeDiff());
                        measurement.put("TempInside", spd.getInsideTempString());
                        measurement.put("TempOutside", spd.getOutsideTempString());
                    partData.put("Measurement", measurement);
                    partData.put("Signature", spd.getSignature());
                    data.put(partData);
                }
            object.put("Data", data);
            //Log.i(TAG + ":" + Utils.getLineNumber(), "JSON:\n"+object.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object.toString();
    }
}
