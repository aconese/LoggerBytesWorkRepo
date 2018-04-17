package io.bytesatwork.skycell;

import io.bytesatwork.skycell.connectivity.BleService;
import io.bytesatwork.skycell.sensor.Sensor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class SensorListFragment extends Fragment {
    private static final String TAG = SensorListFragment.class.getSimpleName();

    private MainActivity mActivity;
    private SensorRecyclerViewAdapter mSRVAdapter;

    //Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            //Notify changes to view
            if (BleService.ACTION_SKYCELL_CONNECTED.equals(action)) {
                Log.d(TAG+":"+Utils.getLineNumber(), "View notify add");
            } else if (BleService.ACTION_SKYCELL_DISCONNECTED.equals(action)) {
                Log.d(TAG+":"+Utils.getLineNumber(), "View notify remove: "+intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                mSRVAdapter.notifyDataSetChanged();
                updateSubTitle();
            } else if (BleService.ACTION_SKYCELL_STATE.equals(action)) {
                Log.d(TAG+":"+Utils.getLineNumber(), "View notify add/changed (state): "+intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
                mSRVAdapter.notifyDataSetChanged();
                updateSubTitle();
            } else if (BleService.ACTION_SKYCELL_DATA.equals(action)) {
                Log.d(TAG+":"+Utils.getLineNumber(), "View notify changed (data): "+intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
            } else if (BleService.ACTION_SKYCELL_DATA_ALL.equals(action)) {
                Log.d(TAG+":"+Utils.getLineNumber(), "View notify changed (data): "+intent.getStringExtra(BleService.DEVICE_ADDRESS_SKYCELL));
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
    public SensorListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //mHandler = new Handler();
        mActivity = (MainActivity)getActivity();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View myView = inflater.inflate(R.layout.sensor_list, container, false);
        return myView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Resume()");

        RecyclerView recyclerView = mActivity.findViewById(R.id.sensor_list);
        if (recyclerView != null) {
            setupRecyclerView(recyclerView);
        } else {
            mActivity.finish();
        }

        mActivity.registerReceiver(mGattUpdateReceiver, setupBroadcastReceiverFilter());
        //mActivity.mBleService.advertise(false); //Start permanent advertising
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Pause()");

        //mActivity.mBleService.advertise(true); //Start permanent advertising
        mActivity.unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Stop()");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Do something that differs the Activity's menu here
        // Inflate the menu; this adds items to the action bar if it is present.

        Toolbar toolbar = mActivity.findViewById(R.id.toolbar);
        toolbar.setTitle(mActivity.getTitle());
        updateSubTitle();
        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setIcon(R.mipmap.ic_launcher_foreground);
            //actionBar.setHomeAsUpIndicator(R.mipmap.ic_launcher_foreground);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setHomeButtonEnabled(false);//true
            actionBar.setDisplayShowHomeEnabled(true);//false
            actionBar.setDisplayHomeAsUpEnabled(false);//true
            actionBar.setDisplayShowCustomEnabled(true);
        }

        inflater.inflate(R.menu.menu_sensor_list, menu);
        Drawable update = ContextCompat.getDrawable(mActivity.app.getApplicationContext(), R.drawable.ic_update);
        update.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorSkyCellBlue), PorterDuff.Mode.SRC_ATOP);
        menu.findItem(R.id.menu_advertise).setIcon(update);

        Drawable settings = ContextCompat.getDrawable(mActivity.app.getApplicationContext(), android.R.drawable.ic_menu_preferences);
        settings.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorSkyCellBlue), PorterDuff.Mode.SRC_ATOP);
        menu.findItem(R.id.menu_settings).setIcon(settings);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG+":"+Utils.getLineNumber(), "Menuitem: "+item.getItemId()+" selected");
        switch (item.getItemId()) {
            case android.R.id.home:
                Log.i(TAG+":"+Utils.getLineNumber(), TAG+"Menu Home selected");
                break;
            case R.id.menu_advertise:
                Log.i(TAG+":"+Utils.getLineNumber(), TAG+"Menu Advertise selected");
                break;
            case R.id.menu_settings:
                Log.i(TAG+":"+Utils.getLineNumber(), TAG+"Menu Settings selected");
                mActivity.changeFragment(mActivity.FRAGMENT_SETTINGS, mActivity.mCurrentSensorAddress);
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateSubTitle() {
        Toolbar toolbar = mActivity.findViewById(R.id.toolbar);
        toolbar.setSubtitle(mActivity.mSensorViewList.size()+" "+getString(R.string.title_sensor_list_sub));
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        mSRVAdapter = new SensorRecyclerViewAdapter(mActivity);
        recyclerView.setAdapter(mSRVAdapter);
        //add divider between rows
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
    }

    public static class SensorRecyclerViewAdapter
            extends RecyclerView.Adapter<SensorRecyclerViewAdapter.SensorListViewHolder> {

        private final MainActivity mParentActivity;
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String sensorAddress = (String) view.getTag();
                Log.i(TAG + ":" + Utils.getLineNumber(), sensorAddress);
                mParentActivity.changeFragment(mParentActivity.FRAGMENT_SENSOR_DETAIL, sensorAddress);
            }
        };
        private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Sensor sensor = mParentActivity.app.mSensorList.getSensorByAddress((String) view.getTag());
                if (sensor != null) {
                    mParentActivity.mBleService.sendDisconnect(sensor.getAddress());
                    return true;
                }

                return false;
            }
        };
        private final View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int colorPrimary = ContextCompat.getColor(mParentActivity.app.getAppContext(), R.color.colorPrimary);
                if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
                    view.setBackgroundColor(ContextCompat.getColor(mParentActivity.app.getAppContext(), R.color.colorSkyCellBlue));
                    ((TextView) view.findViewById(R.id.id)).setTextColor(colorPrimary);
                    ((TextView) view.findViewById(R.id.utcStart)).setTextColor(colorPrimary);
                    ((TextView) view.findViewById(R.id.min)).setTextColor(colorPrimary);
                    ((TextView) view.findViewById(R.id.max)).setTextColor(colorPrimary);
                    ((TextView) view.findViewById(R.id.rssi)).setTextColor(colorPrimary);
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
                {
                    int colorText = ContextCompat.getColor(mParentActivity.app.getAppContext(), R.color.colorText);
                    view.setBackgroundColor(colorPrimary);
                    ((TextView) view.findViewById(R.id.id)).setTextColor(ContextCompat.getColor(mParentActivity.app.getAppContext(), R.color.colorTitle));
                    ((TextView) view.findViewById(R.id.utcStart)).setTextColor(colorText);
                    ((TextView) view.findViewById(R.id.min)).setTextColor(colorText);
                    ((TextView) view.findViewById(R.id.max)).setTextColor(colorText);
                    ((TextView) view.findViewById(R.id.rssi)).setTextColor(colorText);
                }
                return false;
            }
        };

        SensorRecyclerViewAdapter(MainActivity parent) {
            mParentActivity = parent;
        }

        @Override
        public SensorListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.sensor_list_row, parent, false);
            return new SensorListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final SensorListViewHolder holder, int position) {
            String sensorAddress = mParentActivity.mSensorViewList.get(position);
            Sensor sensor = mParentActivity.app.mSensorList.getSensorByAddress(sensorAddress);
            if (sensor != null) {
                Drawable drawable = holder.mContainerImage.getDrawable().mutate();
                if (position % 2 == 0) {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "addr: " + sensorAddress + " position " + position + " change color " + holder.mContainerImage.getDrawable().getColorFilter() + " to blue");
                    drawable.clearColorFilter();
                    drawable.setColorFilter(ContextCompat.getColor(mParentActivity.app.getAppContext(), R.color.colorContainerBlue), PorterDuff.Mode.SRC_ATOP);
                } else {
                    Log.d(TAG + ":" + Utils.getLineNumber(), "addr: " + sensorAddress + " position " + position + " change color " + holder.mContainerImage.getDrawable().getColorFilter() + " to red");
                    drawable.clearColorFilter();
                    drawable.setColorFilter(ContextCompat.getColor(mParentActivity.app.getAppContext(), R.color.colorContainerRed), PorterDuff.Mode.SRC_ATOP);
                }
                holder.mIdView.setText(mParentActivity.app.getApplicationContext().getString(R.string.label_id)
                        + ": " + sensor.getIdString());
                holder.mUtcStartView.setText(mParentActivity.app.getApplicationContext().getString(R.string.label_utc_start)
                        + ": " + sensor.mState.getUtcStartString());
                holder.mMinView.setText(mParentActivity.app.getApplicationContext().getString(R.string.label_min_temp_inside)
                        + ": " + sensor.mState.getMinInsideTempString());
                holder.mMaxView.setText(mParentActivity.app.getApplicationContext().getString(R.string.label_max_temp_inside)
                        + ": " + sensor.mState.getMaxInsideTempString());
                holder.mRssiView.setText(mParentActivity.app.getApplicationContext().getString(R.string.label_rssi)
                        + ": " + Integer.toString(sensor.mState.getRssi()));

                holder.itemView.setTag(sensorAddress);
                holder.itemView.setOnClickListener(mOnClickListener);
                holder.itemView.setOnLongClickListener(mOnLongClickListener);
                holder.itemView.setOnTouchListener(mOnTouchListener);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return mParentActivity.mSensorViewList.size();
        }

        class SensorListViewHolder extends RecyclerView.ViewHolder {
            final ImageView mContainerImage;
            final TextView mIdView;
            final TextView mUtcStartView;
            final TextView mMinView;
            final TextView mMaxView;
            final TextView mRssiView;

            SensorListViewHolder(View view) {
                super(view);
                mContainerImage = view.findViewById(R.id.containerImage);
                mIdView = view.findViewById(R.id.id);
                mUtcStartView = view.findViewById(R.id.utcStart);
                mMinView = view.findViewById(R.id.min);
                mMaxView = view.findViewById(R.id.max);
                mRssiView = view.findViewById(R.id.rssi);
            }
        }
    }
}
