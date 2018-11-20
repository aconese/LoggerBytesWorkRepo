package io.bytesatwork.skycell;

import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.sensor.SensorList;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_ACCESS_COARSE_LOCATION = 1;

    public static final String FRAGMENT_SENSOR_LIST = "io.bytesatwork.skycell.FRAGMENT_SENSOR_LIST";
    public static final String FRAGMENT_SENSOR_DETAIL = "io.bytesatwork.skycell.FRAGMENT_SENSOR_DETAIL";
    public static final String FRAGMENT_SETTINGS = "io.bytesatwork.skycell.FRAGMENT_SETTINGS";
    public static final String FRAGMENT_BROWSER = "io.bytesatwork.skycell.FRAGMENT_BROWSER";

    public static final String SHARED_PREFERENCES = "io.bytesatwork.skycell.SHARED_PREFERENCES";
    public static final String SHARED_PREFERENCES_SERVER_IP = "io.bytesatwork.skycell.SHARED_PREFERENCES_SERVER_IP";

    public static final String MIME_TEXT_PLAIN = "text/plain";

    public SkyCellApplication app = ((SkyCellApplication) SkyCellApplication.getAppContext());
    public BluetoothAdapter mBluetoothAdapter;
    public static BleService mBleService = null;
    public static boolean mBleServiceBound = false;
    public String mCurrentSensorAddress = "";
    public List<String> mSensorViewList = Collections.synchronizedList(new ArrayList<String>());
    private SharedPreferences mPreferences;
    public NfcAdapter mNfcAdapter;

    //Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BleService.ACTION_SKYCELL_CONNECTED.equals(action)) {
            } else if (BleService.ACTION_SKYCELL_DISCONNECTED.equals(action)) {
                mSensorViewList.remove(intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
            } else if (BleService.ACTION_SKYCELL_STATE.equals(action)) {
                if (!mSensorViewList.contains(intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL))) {
                    mSensorViewList.add(intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                }
            } else if (BleService.ACTION_SKYCELL_DATA.equals(action)) {
            } else if (BleService.ACTION_SKYCELL_DATA_ALL.equals(action)) {
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_ON:
                        //Indicates the local Bluetooth adapter is turning on. However local clients should wait for STATE_ON before attempting to use the adapter.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_TURNING_ON");
                        break;

                    case BluetoothAdapter.STATE_ON:
                        //Indicates the local Bluetooth adapter is on, and ready for use.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_ON");
                        break;

                    case BluetoothAdapter.STATE_TURNING_OFF:
                        //Indicates the local Bluetooth adapter is turning off. Local clients should immediately attempt graceful disconnection of any remote links.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_TURNING_OFF");
                        break;

                    case BluetoothAdapter.STATE_OFF:
                        //Indicates the local Bluetooth adapter is off.
                        Log.i(TAG+":"+Utils.getLineNumber(), "BluetoothAdapter.STATE_OFF");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(app.getApplicationContext(), R.string.ble_is_off, Toast.LENGTH_SHORT).show();
                            }
                        });
                        mSensorViewList.clear();
                        break;

                    default:
                        break;
                }
            }
        }
    };

    private static IntentFilter setupBroadcastReceiverFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_SKYCELL_CONNECTED);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_SKYCELL_STATE);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DATA);
        intentFilter.addAction(BleService.ACTION_SKYCELL_DATA_ALL);
        intentFilter.addAction(BleService.ACTION_SKYCELL_EXTREMA);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.setPriority(Constants.SKYCELL_INTENT_FILTER_HIGH_PRIORITY);
        return intentFilter;
    }

    private static IntentFilter setupNFCReceiverFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            intentFilter.addDataType(MIME_TEXT_PLAIN);
        } catch(IntentFilter.MalformedMimeTypeException e) {
            Log.e(TAG+":"+Utils.getLineNumber(), e.toString());
        }
        return intentFilter;
    }

    //Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "onServiceConnected");
            mBleService = ((BleService.LocalBinder) service).getService();
            if (!mBleService.initialize()) {
                Log.e(TAG + ":" + Utils.getLineNumber(), "Unable to initialize Bluetooth");
                finish();
            }
            mBleServiceBound = true;

            if (mBluetoothAdapter.isEnabled()) {
                mBleService.advertise(true); //Start permanent advertising
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "permission granted");
                    mBleService.scan(true, Constants.SKYCELL_SERVICE);
                } else {
                    Log.d(TAG, "no permission");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG + ":" + Utils.getLineNumber(), "onServiceDisconnected");
            mBleService.advertise(false); //Stop permanent advertising
            mBleService = null;
            mBleServiceBound = false;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.e(TAG + ":" + Utils.getLineNumber(), "onBindingDied");
            finish();
        }

        public void onNullBinding(ComponentName name) {
            Log.e(TAG+":"+Utils.getLineNumber(),"onNullBinding");
            finish();
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
        int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG+":"+Utils.getLineNumber(), "Coarse Location Permission granted");
                    if (mBleServiceBound && mBleService != null && mBluetoothAdapter.isEnabled()) {
                        mBleService.scan(true, Constants.SKYCELL_SERVICE);
                    }
                }
                break;
            default:
                break;
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        Log.i(TAG+":"+Utils.getLineNumber(), "SAVE sensors");
        //savedInstanceState.putParcelableArrayList(SKYCELL_SENSORS, mSensorList.getAllSensors());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG+":"+Utils.getLineNumber(), "onCreate");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            if (app != null) {
                app.mSensorList = new SensorList();
                changeFragment(FRAGMENT_SENSOR_LIST, "");
            } else {
                Log.i(TAG+":"+Utils.getLineNumber(), "APP IS NULL!!!!");
            }
        } else {
            //RESTORE devices
            Log.i(TAG+":"+Utils.getLineNumber(), "RESTORE devices");
            //ArrayList<Sensor> sensors = savedInstanceState.getParcelableArrayList(SKYCELL_SENSORS);
            //mSensorList = new SensorList(sensors);
        }

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.ble_not_supported));
            finish();
        }

        //Check for advertisement support
        if (!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()) {
            Toast.makeText(this, R.string.ble_adv_not_supported, Toast.LENGTH_SHORT).show();
            Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.ble_adv_not_supported));
            finish();
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "request permission");
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                PERMISSION_REQUEST_ACCESS_COARSE_LOCATION);
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mNfcAdapter = NfcAdapter.getDefaultAdapter(app.getApplicationContext());
        if (mNfcAdapter == null || !mNfcAdapter.isEnabled()) {
            Log.e(TAG+":"+Utils.getLineNumber(), getString(R.string.nfc_is_off));
            //startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
            //finish();
        }

        mPreferences = getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);

        handleIntent(getIntent());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG+":"+Utils.getLineNumber(), "Menu: "+menu.toString());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG+":"+Utils.getLineNumber(), "Menuitem: "+item.getItemId()+" selected");
        switch (item.getItemId()) {
            case android.R.id.home:
                Log.i(TAG + ":" + Utils.getLineNumber(), TAG + "Menu Home selected");
                return false;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" onBackPressed()");
        Fragment detailFragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_SENSOR_DETAIL);
        Fragment settingsFragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_SETTINGS);
        if ((detailFragment != null && detailFragment.isVisible())
            || (settingsFragment != null && settingsFragment.isVisible())) {
            changeFragment(FRAGMENT_SENSOR_LIST, mCurrentSensorAddress);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Resume()");

        //Ensures Bluetooth is enabled on the device.
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }

        registerReceiver(mGattUpdateReceiver, setupBroadcastReceiverFilter());
        if (mNfcAdapter != null) {
            //registerReceiver(mNFCReceiver, setupNFCReceiverFilter());
            final PendingIntent pendingIntent = PendingIntent.getActivity(app.getApplicationContext(), 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            IntentFilter[] filters = new IntentFilter[1];
            filters[0] = setupNFCReceiverFilter();
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, new String[][]{});
        }

        //Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG+":"+Utils.getLineNumber(), "Pause()");

        unregisterReceiver(mGattUpdateReceiver);

        if (mNfcAdapter != null) {
            //unregisterReceiver(mNFCReceiver);
            mNfcAdapter.disableForegroundDispatch(this);
        }

        //Clear keep screen on
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG+":"+Utils.getLineNumber(), "onStart");

        //Ensures Bluetooth is enabled on the device.
        /*if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }*/

        if (mBleService == null) {
            app.getApplicationContext().bindService(new Intent(this, BleService.class),
                mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        //Empty send queue
        if (mBleService != null) mBleService.emptySendQueue();

        Log.d(TAG+":"+Utils.getLineNumber(), "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG+":"+Utils.getLineNumber(), "onDestroy");

        if (mBleServiceBound && mBleService != null) {
            app.getApplicationContext().unbindService(mServiceConnection);
            mBleServiceBound = false;
        }
        mBleService = null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();

            if (action != null && action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                String type = intent.getType();

                if (MIME_TEXT_PLAIN.equals(type)) {
                    Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                    Ndef ndef = Ndef.get(tag);
                    if (ndef != null) {
                        NdefMessage ndefMessage = ndef.getCachedNdefMessage();

                        NdefRecord[] records = ndefMessage.getRecords();
                        for (NdefRecord ndefRecord : records) {
                            if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                                try {
                                    final String text = readText(ndefRecord);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(app.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    Log.i(TAG + ":" + Utils.getLineNumber(), text);
                                } catch (UnsupportedEncodingException e) {
                                    Log.e(TAG + ":" + Utils.getLineNumber(), e.getMessage());
                                }
                            }
                        }
                    }
                } else {
                    Log.i(TAG + ":" + Utils.getLineNumber(), "Wrong mime type: " + type);
                }
            }
        }
    }

    private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

        byte[] payload = record.getPayload();

        //Get the Text Encoding
        Charset charset = ((payload[0] & 128) == 0) ? StandardCharsets.UTF_8 :
            StandardCharsets.UTF_16;

        //Get the Language Code
        int languageCodeLength = payload[0] & 0063;

        // Get the Text
        return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, charset);
    }

    public void changeFragment(String tag, String currentSensorAddress) {
        mCurrentSensorAddress = currentSensorAddress;
        Fragment newFragment = null;
        if (tag.equals(FRAGMENT_SENSOR_LIST)) {
            newFragment = new SensorListFragment();
        } else if (tag.equals(FRAGMENT_SENSOR_DETAIL)) {
            newFragment = new SensorDetailFragment();
        } else if (tag.equals(FRAGMENT_SETTINGS)) {
            newFragment = new SettingsFragment();
        } else if (tag.equals(FRAGMENT_BROWSER)) {
            newFragment = new BrowserFragment();
        }

        if (newFragment != null) {
            // Insert the fragment by replacing any existing fragment
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, newFragment, tag)
                    //.addToBackStack(null) //Enable back key
                    .commit();
        }
    }

    public void saveSettings(String ip) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(SHARED_PREFERENCES_SERVER_IP, ip);
        editor.commit();
    }

    public String getSettings() {
        return mPreferences.getString(SHARED_PREFERENCES_SERVER_IP, getString(R.string.default_pref_server_ip));
    }
}
