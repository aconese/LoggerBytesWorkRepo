/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.sensor.Sensor;
import io.bytesatwork.skycell.Constants;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SensorDetailFragment extends Fragment {
    private static final String TAG = SensorDetailFragment.class.getSimpleName();

    private MainActivity mActivity;
    private AlertDialog mProgressDialog;
    private PagerAdapter mPagerAdapter;
    private ExecutorService mExecutor;

    //Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            //Notify changes to view
            if (mActivity.mCurrentSensorAddress.equals(intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL))) {
                if (BleService.ACTION_SKYCELL_CONNECTED.equals(action)) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "View notify add");
                } else if (BleService.ACTION_SKYCELL_DISCONNECTED.equals(action)) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "View notify remove: " + intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                    hideProgress();
                    //TODO: gray out
                } else if (BleService.ACTION_SKYCELL_STATE.equals(action)) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "View notify changed (state): " + intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                } else if (BleService.ACTION_SKYCELL_DATA.equals(action)) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "View notify changed (data): " + intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                    Sensor sensor = mActivity.app.mSensorList.getSensorByAddress(mActivity.mCurrentSensorAddress);
                    if (sensor != null) {
                        updateProgress(sensor.mData.size());
                    } else {
                        hideProgress();
                        //TODO: gray out
                    }
                } else if (BleService.ACTION_SKYCELL_DATA_ALL.equals(action)) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "View notify changed (all data): " + intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                    hideProgress();
                    ViewPager pager = getView().findViewById(R.id.pager);
                    pager.setVisibility(View.VISIBLE);
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
        intentFilter.setPriority(Constants.SKYCELL_INTENT_FILTER_LOW_PRIORITY);
        return intentFilter;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SensorDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (MainActivity)getActivity();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View myView = inflater.inflate(R.layout.sensor_detail, container, false);

        Sensor sensor = mActivity.app.mSensorList.getSensorByAddress(mActivity.mCurrentSensorAddress);
        if (sensor != null) {
            //Details
            TextView id = myView.findViewById(R.id.id);
            id.setText(mActivity.app.getApplicationContext().getString(R.string.label_id)+":");
            TextView idValue = myView.findViewById(R.id.idValue);
            idValue.setText(sensor.getDeviceId());
            TextView utcStart = myView.findViewById(R.id.utcStart);
            utcStart.setText(mActivity.app.getApplicationContext().getString(R.string.label_utc_start)+":");
            TextView utcStartValue = myView.findViewById(R.id.utcStartValue);
            utcStartValue.setText(0);
            ImageView batteryImage = myView.findViewById(R.id.batteryImage);
            batteryImage.setImageResource(R.drawable.ic_battery);
            batteryImage.getDrawable().clearColorFilter();
            //TODO: check battery level
            //if (sensor.mState.getBattery() )
            batteryImage.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorBatteryGreen), PorterDuff.Mode.SRC_ATOP);
            TextView batteryValue = myView.findViewById(R.id.batteryValue);
            batteryValue.setText(sensor.mState.getBatteryString());

            //Data
            ViewPager pager = myView.findViewById(R.id.pager);
            pager.setVisibility(View.INVISIBLE);

            //Upload button
            final Button uploadButton = myView.findViewById(R.id.uploadButton);
            Drawable[] drawables = uploadButton.getCompoundDrawables();
            for (Drawable drawable : drawables) {
                if(drawable != null) {
                    drawable.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorPrimary), PorterDuff.Mode.SRC_ATOP);
                }
            }
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    uploadButton.setEnabled(false);
                    Sensor sensor = mActivity.app.mSensorList.getSensorByAddress(mActivity.mCurrentSensorAddress);
                    if (sensor != null) {
                        Toast.makeText(mActivity, R.string.start_upload, Toast.LENGTH_SHORT).show();
                        String json = sensor.convertToJSONString();
                        if (writeFile(json, mActivity.mCurrentSensorAddress + ".txt")) {
                            Log.i(TAG + ":" + Utils.getLineNumber(), "File write ok");
                        }
                        mExecutor.execute(new FileUploadTask(json, mActivity.mCurrentSensorAddress +
                            Constants.FILE_ENDING, mActivity.getSettings()));
                        mActivity.mBleService.sendClear(sensor.getAddress());
                    }
                    uploadButton.setEnabled(true);
                    mActivity.changeFragment(mActivity.FRAGMENT_BROWSER, mActivity.mCurrentSensorAddress);
                }
            });

        } else {
            Log.e(TAG + ":" + Utils.getLineNumber(), "Sensor is null!");
        }

        return myView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Resume()");

        ViewPager pager = mActivity.findViewById(R.id.pager);
        if (pager != null) {
            setupPager(pager);
        } else {
            mActivity.finish();
        }

        mActivity.registerReceiver(mGattUpdateReceiver, setupBroadcastReceiverFilter());
        Sensor sensor = mActivity.app.mSensorList.getSensorByAddress(mActivity.mCurrentSensorAddress);
        if (sensor != null) {
            //TODO: check if read is already pending!
            mActivity.mBleService.sendReadData(sensor.getAddress(),
                (CustomTime.getInstance().currentTimeMillis() / 1000));
            showProgress();
        } /*else {
            //TODO: gray out
        } */
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Pause()");

        if (mExecutor != null) mExecutor.shutdown();
        mActivity.unregisterReceiver(mGattUpdateReceiver);
        hideProgress();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Stop()");

        if (mExecutor != null) mExecutor.shutdown();
        hideProgress();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Do something that differs the Activity's menu here
        // Inflate the menu; this adds items to the action bar if it is present.
        Toolbar toolbar = mActivity.findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.title_sensor_detail));
        toolbar.setSubtitle("");
        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            Drawable back = ContextCompat.getDrawable(mActivity.app.getApplicationContext(), R.drawable.ic_back);
            back.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorSkyCellBlue), PorterDuff.Mode.SRC_ATOP);
            actionBar.setHomeAsUpIndicator(back);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowCustomEnabled(true);
        }

        inflater.inflate(R.menu.menu_sensor_detail, menu);
        Drawable update = ContextCompat.getDrawable(mActivity.app.getApplicationContext(), R.drawable.ic_update);
        update.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorSkyCellBlue), PorterDuff.Mode.SRC_ATOP);
        menu.findItem(R.id.menu_advertise).setIcon(update);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG+":"+Utils.getLineNumber(), "Menuitem: "+item.getItemId()+" selected");
        switch (item.getItemId()) {
            case android.R.id.home:
                Log.i(TAG+":"+Utils.getLineNumber(), TAG+"Menu Home selected");
                mActivity.changeFragment(mActivity.FRAGMENT_SENSOR_LIST, mActivity.mCurrentSensorAddress);
                return true;
            case R.id.menu_advertise:
                Log.i(TAG+":"+Utils.getLineNumber(), TAG+"Menu Advertise selected");
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showProgress() {
        if (mProgressDialog == null) {
            View progressDialog = getLayoutInflater().inflate(R.layout.progress_dialog, null);

            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setCancelable(false);
            builder.setView(progressDialog);

            mProgressDialog = builder.create();
        }

        mProgressDialog.show();
        TextView progressBarValue = mProgressDialog.findViewById(R.id.progressBarValue);
        if (progressBarValue != null) {
            progressBarValue.setText("0");
        }
        Button progressBarButtonAbort = mProgressDialog.findViewById(R.id.progressBarButtonAbort);
        if (progressBarButtonAbort != null) {
            progressBarButtonAbort.setVisibility(View.GONE);
            progressBarButtonAbort.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    //TODO: implement abort
                }
            });
        }
    }

    private void updateProgress(int progress) {
        Log.d(TAG+":"+Utils.getLineNumber(), TAG+"progress: "+progress);
        TextView progressBarValue = mProgressDialog.findViewById(R.id.progressBarValue);
        if (progressBarValue != null) {
            progressBarValue.setText(Integer.toString(progress));
        }
    }

    public void hideProgress() {
        if (mProgressDialog != null) mProgressDialog.dismiss();
    }

    private void setupPager(@NonNull ViewPager pager) {
        mPagerAdapter = new ScreenSlidePagerAdapter(getChildFragmentManager());
        pager.setAdapter(mPagerAdapter);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                Log.i(TAG+":"+Utils.getLineNumber(), "onPageSelected: "+position);
                mActivity.invalidateOptionsMenu();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return ImageFragment.create(position);
        }

        @Override
        public int getCount() {
            return ImageFragment.NUM_PAGES;
        }
    }

    private boolean writeFile(String json, String filename) {
        boolean ok = false;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(mActivity.app.getApplicationContext().getExternalFilesDir(null).getAbsolutePath(), filename);
            Log.i(TAG + ":" + Utils.getLineNumber(), "Write: File: " + file.getAbsolutePath());
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write(json.getBytes());

                //Close file
                fos.flush();
                fos.close();
                ok = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ok;
    }

    private class FileUploadTask implements Runnable {
        String mJson;
        String mFilename;
        String mIp;

        public FileUploadTask(String json, String filename, String ip) {
            this.mJson = json;
            mFilename = filename;
            this.mIp = ip;
        }

        public void run() {
            HttpURLConnection connection = null;
            try {
                //header
                Log.i(TAG + ":" + Utils.getLineNumber(), "Send JSON to: http://" + mIp + "/skycell/" + mFilename);
                URL url = new URL("http://" + mIp + "/skycell/" + mFilename);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                //send json
                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                out.writeBytes(mJson);
                out.flush();
                out.close();

                //read response
                InputStream responseStream = new BufferedInputStream(connection.getInputStream());
                BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));
                String line = "";
                while ((line = responseStreamReader.readLine()) != null) {
                    Log.i(TAG + ":" + Utils.getLineNumber(), line);
                }
                responseStreamReader.close();

                //Get Status
                Log.i(TAG + ":" + Utils.getLineNumber(), "Status: " + connection.getResponseCode()); //HttpURLConnection.HTTP_OK

                //disconnect
                connection.disconnect();

                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mActivity, R.string.upload_success, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mActivity, R.string.upload_failed, Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
