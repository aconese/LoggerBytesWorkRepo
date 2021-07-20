/* Copyright (c) 2021 bytes at work AG. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * bytes at work AG. ("Confidential Information"). You shall not disclose
 * such confidential information and shall use it only in accordance with
 * the terms of the license agreement you entered into with bytes at work AG.
 */

package io.bytesatwork.skycell;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class BrowserFragment extends Fragment {
    private static final String TAG = BrowserFragment.class.getSimpleName();

    private MainActivity mActivity;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public BrowserFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (MainActivity)getActivity();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View myView = inflater.inflate(R.layout.browser, container, false);

        WebView webView = myView.findViewById(R.id.webView);
        if (webView != null) {
            WebSettings webViewSettings = webView.getSettings();
            webViewSettings.setJavaScriptEnabled(true);

            webView.addJavascriptInterface(new WebAppInterface(), "Android");
            webView.setWebViewClient(new MyWebViewClient());

            /*String html = "<html><body><h1>Hello, WebView</h1></body></html>";
            webView.loadData(html, "text/html", "UTF-8");*/
            webView.loadUrl("file:///android_asset/plot.html");
        }

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
        toolbar.setTitle(R.string.title_browser);
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
                mActivity.changeFragment(mActivity.FRAGMENT_SENSOR_LIST, mActivity.mCurrentSensorAddress); //TODO: Switch back to SensorDetailFragment
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private class WebAppInterface {

        WebAppInterface() {
        }

        /** Show a toast from the web page */
        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mActivity, toast, Toast.LENGTH_SHORT).show();
        }
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            /*if (Uri.parse(url).getHost().equals("www.example.com")) {
                // This is my web site, so do not override; let my WebView load the page
                return false;
            }
            // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
            return true;*/
            return false;
        }
    }
}
