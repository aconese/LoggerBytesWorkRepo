package io.bytesatwork.skycell;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

public class SettingsFragment extends Fragment {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    private MainActivity mActivity;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SettingsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (MainActivity)getActivity();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View myView = inflater.inflate(R.layout.settings, container, false);

        final TextInputEditText editTextServerIp = myView.findViewById(R.id.editTextServerIp);
        editTextServerIp.setText(mActivity.getSettings());
        editTextServerIp.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if(view.getId() == R.id.editTextServerIp && !hasFocus) {

                    InputMethodManager imm =  (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    mActivity.saveSettings(editTextServerIp.getText().toString());
                }
            }
        });

        return myView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Resume()");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG+":"+Utils.getLineNumber(), TAG+" Pause()");
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
        toolbar.setTitle(R.string.title_settings);
        toolbar.setSubtitle("");
        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            Drawable back = ContextCompat.getDrawable(mActivity.app.getApplicationContext(), R.drawable.ic_back);
            back.setColorFilter(ContextCompat.getColor(mActivity.app.getAppContext(), R.color.colorSkyCellBlue), PorterDuff.Mode.SRC_ATOP);
            actionBar.setHomeAsUpIndicator(back);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
            //actionBar.setDisplayShowCustomEnabled(true);
        }

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
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
